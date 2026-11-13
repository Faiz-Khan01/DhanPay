package com.dhanpay.service;

import com.dhanpay.dto.transfer.TransferHistoryItemDto;
import com.dhanpay.dto.transfer.TransferJournalDto;
import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.dto.wallet.WalletLimitsDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.idempotency.IdempotencyService;
import com.dhanpay.idempotency.IdempotencyState;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.repository.TransferRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Executes peer-to-peer fund transfers with deadlock-free pessimistic locking and idempotency protection.
 */
@Service
public class TransferService {

    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("1.0000");
    private static final BigDecimal MAX_SINGLE_TRANSFER_AMOUNT = new BigDecimal("50000.0000");
    private static final BigDecimal MAX_DAILY_TRANSFER_AMOUNT = new BigDecimal("100000.0000");
    private static final int MONETARY_SCALE = 4;
    private static final String REF_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int REF_SUFFIX_LENGTH = 6;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private NotificationStreamService notificationStreamService;
    private TwoFactorAuthService twoFactorAuthService;

    @Autowired(required = false)
    public void setNotificationStreamService(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    @Autowired(required = false)
    public void setTwoFactorAuthService(TwoFactorAuthService twoFactorAuthService) {
        this.twoFactorAuthService = twoFactorAuthService;
    }

    public TransferService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            LedgerService ledgerService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes transfer within an isolated READ_COMMITTED transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TransferResponseDto executeTransfer(
            UserPrincipal senderPrincipal,
            String idempotencyKey,
            TransferRequestDto request,
            HttpServletRequest httpRequest) {
        Objects.requireNonNull(senderPrincipal, "senderPrincipal must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        String trimmedKey = idempotencyKey.trim();

        // 1. Compute SHA-256 fingerprint & check idempotency cache
        String fingerprint = idempotencyService.computeFingerprint(
                request.recipientWalletReference(),
                request.amount(),
                request.description()
        );

        User senderUser = userRepository.findById(senderPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Sender user not found"));

        IdempotencyState idempotencyState = idempotencyService.lockOrRetrieve(senderUser, trimmedKey, fingerprint);
        if (idempotencyState.isCompleted()) {
            try {
                return objectMapper.readValue(idempotencyState.cachedResponsePayload(), TransferResponseDto.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached idempotency response", e);
            }
        }

        try {
            // 2. Validate transfer limits
            BigDecimal amount = request.amount().setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
            if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Transfer amount must be at least ₹1.0000");
            }
            if (amount.compareTo(MAX_SINGLE_TRANSFER_AMOUNT) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_LIMIT", "Transfer amount cannot exceed ₹50,000.0000");
            }

            Wallet senderWallet = walletRepository.findByUserId(senderUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Sender wallet not found"));

            if (senderWallet.getWalletReference().equalsIgnoreCase(request.recipientWalletReference().trim())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_TRANSFER_PROHIBITED", "Cannot transfer funds to your own wallet");
            }

            Wallet recipientWallet = walletRepository.findByWalletReference(request.recipientWalletReference().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("RECIPIENT_WALLET_NOT_FOUND", "Recipient wallet reference not found"));

            if (Objects.equals(senderWallet.getId(), recipientWallet.getId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_TRANSFER_PROHIBITED", "Cannot transfer funds to your own wallet");
            }

            // 3. Acquire deterministic pessimistic locks (lowest ID first) to eliminate deadlocks
            Long firstLockId = Math.min(senderWallet.getId(), recipientWallet.getId());
            Long secondLockId = Math.max(senderWallet.getId(), recipientWallet.getId());

            Wallet firstLocked = walletRepository.findByIdWithLock(firstLockId)
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found: " + firstLockId));
            Wallet secondLocked = walletRepository.findByIdWithLock(secondLockId)
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found: " + secondLockId));

            Wallet lockedSender = Objects.equals(senderWallet.getId(), firstLockId) ? firstLocked : secondLocked;
            Wallet lockedRecipient = Objects.equals(recipientWallet.getId(), firstLockId) ? firstLocked : secondLocked;

            // 4. Validate wallet status
            if (lockedSender.getStatus() != WalletStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "SENDER_WALLET_INACTIVE", "Sender wallet is " + lockedSender.getStatus());
            }
            if (lockedRecipient.getStatus() != WalletStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "RECIPIENT_WALLET_INACTIVE", "Recipient wallet is " + lockedRecipient.getStatus());
            }

            // 5. Validate sender balance and rolling 24-hour outflow velocity cap
            if (lockedSender.getBalance().compareTo(amount) < 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient wallet balance");
            }

            BigDecimal dailyUsed = transferRepository.sumOutgoingAmountSince(
                    lockedSender.getId(),
                    TransferStatus.SUCCESS,
                    Instant.now().minus(24, ChronoUnit.HOURS)
            );
            BigDecimal normalizedDailyUsed = dailyUsed == null
                    ? BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY)
                    : dailyUsed.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
            if (normalizedDailyUsed.add(amount).compareTo(MAX_DAILY_TRANSFER_AMOUNT) > 0) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "DAILY_LIMIT_EXCEEDED",
                        "Transfer exceeds the ₹1,00,000.00 rolling 24-hour transfer limit"
                );
            }

            // Step-up authentication check for high-value transfers (> ₹10,000)
            if (amount.compareTo(new BigDecimal("10000.0000")) > 0 && senderUser.isTotpEnabled()) {
                String providedCode = request.totpCode();
                if (providedCode == null || providedCode.isBlank()) {
                    providedCode = httpRequest.getHeader("X-DhanPay-2FA-Code");
                }
                if (twoFactorAuthService != null && !twoFactorAuthService.validateStepUp(senderUser, providedCode)) {
                    throw new BusinessException(
                            HttpStatus.PRECONDITION_REQUIRED,
                            "TWO_FACTOR_REQUIRED",
                            "High-value transfers exceeding ₹10,000 require a valid 6-digit 2FA authenticator code"
                    );
                }
            }

            // 6. Deduct and Credit balances
            lockedSender.debit(amount);
            lockedRecipient.credit(amount);
            walletRepository.save(lockedSender);
            walletRepository.save(lockedRecipient);

            // 7. Create Transfer in INITIATED status
            String transferReference = generateUniqueTransferReference();
            Transfer transfer = new Transfer(
                    transferReference,
                    lockedSender,
                    lockedRecipient,
                    amount,
                    lockedSender.getCurrency(),
                    TransferStatus.INITIATED,
                    request.description()
            );
            transfer = transferRepository.save(transfer);

            // 8. Record immutable double-entry ledger transactions & entries
            ledgerService.recordTransfer(transfer, lockedSender, lockedRecipient, amount);

            // 9. Mark transfer SUCCESS
            transfer.setStatus(TransferStatus.SUCCESS);
            transfer = transferRepository.save(transfer);

            // 10. Record audit entry
            String details = String.format("{\"sender\":\"%s\",\"recipient\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\"}",
                    lockedSender.getWalletReference(), lockedRecipient.getWalletReference(), amount, lockedSender.getCurrency());
            auditService.logAction(senderUser.getId(), "TRANSFER_P2P", "TRANSFER", transfer.getReference(), details, httpRequest);

            // 11. Complete idempotency record with cached response payload
            TransferResponseDto responseDto = new TransferResponseDto(
                    transfer.getReference(),
                    lockedSender.getWalletReference(),
                    lockedRecipient.getWalletReference(),
                    amount,
                    lockedSender.getCurrency(),
                    TransferStatus.SUCCESS,
                    transfer.getDescription(),
                    lockedSender.getBalance(),
                    transfer.getCreatedAt()
            );

            String serializedPayload = serializeResponsePayload(responseDto);
            idempotencyService.markSuccess(senderUser.getId(), trimmedKey, transfer, serializedPayload);

            if (notificationStreamService != null) {
                notificationStreamService.sendToUser(
                        lockedRecipient.getUser().getId(),
                        "TRANSFER_RECEIVED",
                        "Incoming Transfer Received",
                        "Received ₹" + amount.setScale(2, RoundingMode.HALF_EVEN) + " from " + senderUser.getFullName(),
                        Map.of("reference", transfer.getReference(), "amount", amount)
                );
            }

            return responseDto;

        } catch (RuntimeException ex) {
            // Clean up/mark failed idempotency status on non-retryable validation or business failure
            idempotencyService.markFailed(senderUser.getId(), trimmedKey);
            throw ex;
        }
    }

    private String serializeResponsePayload(TransferResponseDto responseDto) {
        try {
            return objectMapper.writeValueAsString(responseDto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize transfer response payload", e);
        }
    }

    @Transactional(readOnly = true)
    public WalletLimitsDto getTransferLimits(UserPrincipal principal) {
        Wallet wallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));

        BigDecimal rawUsed = transferRepository.sumOutgoingAmountSince(
                wallet.getId(),
                TransferStatus.SUCCESS,
                Instant.now().minus(24, ChronoUnit.HOURS)
        );
        BigDecimal dailyUsed = rawUsed == null
                ? BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY)
                : rawUsed.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal dailyRemaining = MAX_DAILY_TRANSFER_AMOUNT.subtract(dailyUsed).max(BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY));
        return new WalletLimitsDto(MAX_SINGLE_TRANSFER_AMOUNT, MAX_DAILY_TRANSFER_AMOUNT, dailyUsed, dailyRemaining);
    }

    /**
     * Lists paginated transfer history for the authenticated customer's wallet.
     */
    @Transactional(readOnly = true)
    public Page<TransferHistoryItemDto> getTransferHistory(
            UserPrincipal principal,
            int page,
            int size,
            String search,
            TransferStatus status,
            String direction) {
        Wallet wallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for user"));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        String normalizedDirection = null;
        if (direction != null && !direction.isBlank()) {
            String upper = direction.trim().toUpperCase(Locale.ROOT);
            if (!upper.equals("DEBIT") && !upper.equals("CREDIT")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DIRECTION", "Direction filter must be DEBIT or CREDIT");
            }
            normalizedDirection = upper;
        }

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        return transferRepository.findWalletHistory(wallet.getId(), normalizedDirection, status, normalizedSearch, pageable)
                .map(transfer -> toHistoryItem(transfer, wallet.getId()));
    }

    @Transactional(readOnly = true)
    public TransferJournalDto getTransferJournal(UserPrincipal principal, String transferReference) {
        Transfer transfer = transferRepository.findByReference(transferReference.trim())
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found: " + transferReference));

        boolean isAdmin = principal.getRole() == Role.ROLE_ADMIN;
        if (!isAdmin) {
            Wallet viewerWallet = walletRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for user"));
            boolean isParticipant = Objects.equals(transfer.getSenderWallet().getId(), viewerWallet.getId())
                    || Objects.equals(transfer.getRecipientWallet().getId(), viewerWallet.getId());
            if (!isParticipant) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_JOURNAL_ACCESS", "Not authorized to inspect this transfer journal");
            }
        }

        return ledgerService.getJournalForTransfer(transfer);
    }

    private TransferHistoryItemDto toHistoryItem(Transfer transfer, Long viewerWalletId) {
        boolean isDebit = Objects.equals(transfer.getSenderWallet().getId(), viewerWalletId);
        Wallet counterparty = isDebit ? transfer.getRecipientWallet() : transfer.getSenderWallet();
        return new TransferHistoryItemDto(
                transfer.getReference(),
                isDebit ? "DEBIT" : "CREDIT",
                counterparty.getWalletReference(),
                counterparty.getUser().getFullName(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getDescription(),
                transfer.getFailureReason(),
                transfer.getCreatedAt()
        );
    }

    private String generateUniqueTransferReference() {
        String candidate;
        do {
            candidate = "TXN-" + System.currentTimeMillis() + "-" + randomSuffix();
        } while (transferRepository.existsByReference(candidate));
        return candidate;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(REF_SUFFIX_LENGTH);
        for (int i = 0; i < REF_SUFFIX_LENGTH; i++) {
            sb.append(REF_ALPHABET.charAt(secureRandom.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }
}
