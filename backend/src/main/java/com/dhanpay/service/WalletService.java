package com.dhanpay.service;

import com.dhanpay.dto.wallet.WalletSummaryDto;
import com.dhanpay.dto.wallet.WalletTopUpRequestDto;
import com.dhanpay.dto.wallet.WalletTopUpResponseDto;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.idempotency.IdempotencyService;
import com.dhanpay.idempotency.IdempotencyState;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.ledger.LedgerTransaction;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;

/**
 * Customer wallet inspection and idempotent double-entry funding service.
 */
@Service
public class WalletService {

    private static final BigDecimal MIN_TOPUP_AMOUNT = new BigDecimal("1.0000");
    private static final BigDecimal MAX_TOPUP_AMOUNT = new BigDecimal("100000.0000");
    private static final int MONETARY_SCALE = 4;
    private static final String REF_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public WalletService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerService ledgerService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WalletSummaryDto getMyWallet(UserPrincipal principal) {
        Wallet wallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));

        String ledgerAccountNumber = ledgerAccountRepository.findByWalletId(wallet.getId())
                .map(LedgerAccount::getAccountNumber)
                .orElse(null);

        return new WalletSummaryDto(
                wallet.getWalletReference(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus(),
                ledgerAccountNumber,
                wallet.getUpdatedAt()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public WalletTopUpResponseDto topUpWallet(
            UserPrincipal principal,
            String idempotencyKey,
            WalletTopUpRequestDto request,
            HttpServletRequest httpRequest) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        String trimmedKey = idempotencyKey.trim();
        String sourceLabel = (request.sourceLabel() == null || request.sourceLabel().isBlank())
                ? "UPI / Bank Funding"
                : request.sourceLabel().trim();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Authenticated user not found"));

        String fingerprint = idempotencyService.computeFingerprint(
                "TOPUP:" + user.getId(),
                request.amount(),
                sourceLabel
        );

        IdempotencyState idempotencyState = idempotencyService.lockOrRetrieve(user, trimmedKey, fingerprint);
        if (idempotencyState.isCompleted()) {
            try {
                return objectMapper.readValue(idempotencyState.cachedResponsePayload(), WalletTopUpResponseDto.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached top-up idempotency response", e);
            }
        }

        try {
            BigDecimal amount = request.amount().setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
            if (amount.compareTo(MIN_TOPUP_AMOUNT) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Top-up amount must be at least ₹1.0000");
            }
            if (amount.compareTo(MAX_TOPUP_AMOUNT) > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_LIMIT", "Single top-up cannot exceed ₹1,00,000.0000");
            }

            Wallet userWallet = walletRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));

            Wallet lockedWallet = walletRepository.findByIdWithLock(userWallet.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found: " + userWallet.getId()));

            if (lockedWallet.getStatus() != WalletStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "WALLET_INACTIVE", "Cannot fund wallet in status " + lockedWallet.getStatus());
            }

            lockedWallet.credit(amount);
            walletRepository.save(lockedWallet);

            String topUpReference = generateTopUpReference();
            LedgerTransaction ledgerTx = ledgerService.recordTopUp(topUpReference, lockedWallet, amount);

            String details = String.format("{\"wallet\":\"%s\",\"amount\":\"%s\",\"source\":\"%s\",\"ledgerRef\":\"%s\"}",
                    lockedWallet.getWalletReference(), amount, sourceLabel.replace("\"", "'"), ledgerTx.getReference());
            auditService.logAction(user.getId(), "WALLET_TOPUP", "WALLET", lockedWallet.getWalletReference(), details, httpRequest);

            WalletTopUpResponseDto responseDto = new WalletTopUpResponseDto(
                    topUpReference,
                    lockedWallet.getWalletReference(),
                    ledgerTx.getReference(),
                    amount,
                    lockedWallet.getCurrency(),
                    lockedWallet.getBalance(),
                    sourceLabel,
                    Instant.now()
            );

            idempotencyService.markSuccess(user.getId(), trimmedKey, null, serializePayload(responseDto));
            return responseDto;
        } catch (RuntimeException ex) {
            idempotencyService.markFailed(user.getId(), trimmedKey);
            throw ex;
        }
    }

    private String serializePayload(WalletTopUpResponseDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize top-up response payload", e);
        }
    }

    private String generateTopUpReference() {
        StringBuilder sb = new StringBuilder(16);
        sb.append("TOP-").append(System.currentTimeMillis()).append('-');
        for (int i = 0; i < 4; i++) {
            sb.append(REF_ALPHABET.charAt(secureRandom.nextInt(REF_ALPHABET.length())));
        }
        return sb.toString();
    }
}
