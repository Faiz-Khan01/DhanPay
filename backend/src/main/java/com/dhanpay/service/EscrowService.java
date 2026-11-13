package com.dhanpay.service;

import com.dhanpay.dto.escrow.CreateEscrowHoldRequestDto;
import com.dhanpay.dto.escrow.EscrowHoldItemDto;
import com.dhanpay.entity.EscrowHold;
import com.dhanpay.entity.EscrowStatus;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerAccountStatus;
import com.dhanpay.ledger.LedgerAccountType;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.repository.EscrowHoldRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages buyer-funded escrow holds backed by the GL-SYS-ESCROW-INR-0001 ledger account.
 */
@Service
public class EscrowService {

    private static final int MONETARY_SCALE = 4;
    private static final String SYSTEM_ESCROW_ACCOUNT_NUMBER = "GL-SYS-ESCROW-INR-0001";

    private final EscrowHoldRepository escrowHoldRepository;
    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private NotificationStreamService notificationStreamService;

    @Autowired(required = false)
    public void setNotificationStreamService(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    public EscrowService(
            EscrowHoldRepository escrowHoldRepository,
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerService ledgerService,
            AuditService auditService) {
        this.escrowHoldRepository = escrowHoldRepository;
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<EscrowHoldItemDto> listMyEscrowHolds(UserPrincipal principal) {
        Wallet wallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));
        return escrowHoldRepository.findAllForWallet(wallet.getId()).stream()
                .map(hold -> toDto(hold, wallet.getId()))
                .toList();
    }

    @Transactional
    public EscrowHoldItemDto createHold(
            UserPrincipal buyerPrincipal,
            CreateEscrowHoldRequestDto request,
            HttpServletRequest httpRequest) {
        Wallet buyerLookup = walletRepository.findByUserId(buyerPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Buyer wallet not found"));
        Wallet sellerLookup = walletRepository.findByWalletReference(request.sellerWalletReference().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SELLER_WALLET_NOT_FOUND",
                        "Seller wallet not found: " + request.sellerWalletReference()
                ));

        if (Objects.equals(buyerLookup.getId(), sellerLookup.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_ESCROW_NOT_ALLOWED", "Buyer and seller wallets must be distinct");
        }

        Wallet buyerWallet = walletRepository.findByIdWithLock(buyerLookup.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Buyer wallet not found"));

        if (buyerWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "WALLET_NOT_ACTIVE", "Your wallet is currently " + buyerWallet.getStatus());
        }
        if (sellerLookup.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELLER_WALLET_NOT_ACTIVE", "Seller wallet is not active");
        }

        BigDecimal amount = request.amount().setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        if (buyerWallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE", "Insufficient wallet balance for escrow hold");
        }

        LedgerAccount escrowAccount = resolveOrProvisionEscrowAccount();
        buyerWallet.setBalance(buyerWallet.getBalance().subtract(amount));
        walletRepository.save(buyerWallet);

        String reference = "ESC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String description = (request.description() == null || request.description().isBlank())
                ? "Escrow hold"
                : request.description().trim();

        EscrowHold saved = escrowHoldRepository.save(new EscrowHold(
                reference,
                buyerWallet,
                sellerLookup,
                escrowAccount,
                amount,
                buyerWallet.getCurrency(),
                EscrowStatus.HELD,
                description
        ));

        ledgerService.recordEscrowHold(reference, buyerWallet, escrowAccount, amount);
        auditService.logAction(
                buyerPrincipal.getId(),
                "ESCROW_HELD",
                "ESCROW",
                reference,
                "{\"buyerWallet\":\"" + buyerWallet.getWalletReference()
                        + "\",\"sellerWallet\":\"" + sellerLookup.getWalletReference()
                        + "\",\"amount\":\"" + amount.toPlainString() + "\"}",
                httpRequest
        );
        if (notificationStreamService != null) {
            notificationStreamService.sendToUser(
                    sellerLookup.getUser().getId(),
                    "ESCROW_HELD",
                    "Escrow Hold Created",
                    buyerWallet.getUser().getFullName() + " locked ₹" + amount.setScale(2, RoundingMode.HALF_EVEN) + " in escrow",
                    java.util.Map.of("reference", reference, "amount", amount)
            );
        }

        return toDto(saved, buyerWallet.getId());
    }

    @Transactional
    public EscrowHoldItemDto releaseHold(
            UserPrincipal actorPrincipal,
            String reference,
            HttpServletRequest httpRequest) {
        EscrowHold hold = escrowHoldRepository.findByReferenceWithLock(reference)
                .orElseThrow(() -> new ResourceNotFoundException("ESCROW_NOT_FOUND", "Escrow hold not found: " + reference));

        boolean isAdmin = actorPrincipal.getRole() == Role.ROLE_ADMIN;
        boolean isBuyer = Objects.equals(hold.getBuyerWallet().getUser().getId(), actorPrincipal.getId());
        if (!isBuyer && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ESCROW_RELEASE_FORBIDDEN", "Only the buyer or an admin can release escrow funds");
        }
        if (hold.getStatus() != EscrowStatus.HELD) {
            throw new ConflictException("ESCROW_ALREADY_SETTLED", "Escrow hold " + reference + " is already " + hold.getStatus());
        }

        Wallet sellerWallet = walletRepository.findByIdWithLock(hold.getSellerWallet().getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Seller wallet not found"));
        if (sellerWallet.getStatus() == WalletStatus.CLOSED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WALLET_CLOSED", "Cannot release funds into a closed wallet");
        }

        sellerWallet.setBalance(sellerWallet.getBalance().add(hold.getAmount()));
        walletRepository.save(sellerWallet);

        hold.setStatus(EscrowStatus.RELEASED);
        EscrowHold saved = escrowHoldRepository.save(hold);

        ledgerService.recordEscrowRelease(reference, hold.getEscrowLedgerAccount(), sellerWallet, hold.getAmount());
        auditService.logAction(
                actorPrincipal.getId(),
                "ESCROW_RELEASED",
                "ESCROW",
                reference,
                "{\"sellerWallet\":\"" + sellerWallet.getWalletReference()
                        + "\",\"amount\":\"" + hold.getAmount().toPlainString() + "\"}",
                httpRequest
        );
        if (notificationStreamService != null) {
            notificationStreamService.sendToUser(
                    sellerWallet.getUser().getId(),
                    "ESCROW_RELEASED",
                    "Escrow Released",
                    "₹" + hold.getAmount().setScale(2, RoundingMode.HALF_EVEN) + " released to your wallet (" + reference + ")",
                    java.util.Map.of("reference", reference, "amount", hold.getAmount())
            );
        }

        return toDto(saved, isBuyer ? hold.getBuyerWallet().getId() : sellerWallet.getId());
    }

    @Transactional
    public EscrowHoldItemDto refundHold(
            UserPrincipal actorPrincipal,
            String reference,
            HttpServletRequest httpRequest) {
        EscrowHold hold = escrowHoldRepository.findByReferenceWithLock(reference)
                .orElseThrow(() -> new ResourceNotFoundException("ESCROW_NOT_FOUND", "Escrow hold not found: " + reference));

        boolean isAdmin = actorPrincipal.getRole() == Role.ROLE_ADMIN;
        boolean isSeller = Objects.equals(hold.getSellerWallet().getUser().getId(), actorPrincipal.getId());
        if (!isSeller && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ESCROW_REFUND_FORBIDDEN", "Only the seller or an admin can refund an escrow hold");
        }
        if (hold.getStatus() != EscrowStatus.HELD) {
            throw new ConflictException("ESCROW_ALREADY_SETTLED", "Escrow hold " + reference + " is already " + hold.getStatus());
        }

        Wallet buyerWallet = walletRepository.findByIdWithLock(hold.getBuyerWallet().getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Buyer wallet not found"));
        if (buyerWallet.getStatus() == WalletStatus.CLOSED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WALLET_CLOSED", "Cannot refund into a closed wallet");
        }

        buyerWallet.setBalance(buyerWallet.getBalance().add(hold.getAmount()));
        walletRepository.save(buyerWallet);

        hold.setStatus(EscrowStatus.REFUNDED);
        EscrowHold saved = escrowHoldRepository.save(hold);

        ledgerService.recordEscrowRefund(reference, hold.getEscrowLedgerAccount(), buyerWallet, hold.getAmount());
        auditService.logAction(
                actorPrincipal.getId(),
                "ESCROW_REFUNDED",
                "ESCROW",
                reference,
                "{\"buyerWallet\":\"" + buyerWallet.getWalletReference()
                        + "\",\"amount\":\"" + hold.getAmount().toPlainString() + "\"}",
                httpRequest
        );
        if (notificationStreamService != null) {
            notificationStreamService.sendToUser(
                    buyerWallet.getUser().getId(),
                    "ESCROW_REFUNDED",
                    "Escrow Refunded",
                    "₹" + hold.getAmount().setScale(2, RoundingMode.HALF_EVEN) + " refunded to your wallet (" + reference + ")",
                    java.util.Map.of("reference", reference, "amount", hold.getAmount())
            );
        }

        return toDto(saved, isSeller ? hold.getSellerWallet().getId() : buyerWallet.getId());
    }

    private LedgerAccount resolveOrProvisionEscrowAccount() {
        return ledgerAccountRepository.findByAccountNumber(SYSTEM_ESCROW_ACCOUNT_NUMBER)
                .orElseGet(() -> ledgerAccountRepository.save(new LedgerAccount(
                        null,
                        SYSTEM_ESCROW_ACCOUNT_NUMBER,
                        LedgerAccountType.ESCROW,
                        "INR",
                        LedgerAccountStatus.ACTIVE
                )));
    }

    private static EscrowHoldItemDto toDto(EscrowHold hold, Long viewerWalletId) {
        String role = Objects.equals(hold.getBuyerWallet().getId(), viewerWalletId) ? "BUYER" : "SELLER";
        return new EscrowHoldItemDto(
                hold.getReference(),
                role,
                hold.getBuyerWallet().getWalletReference(),
                hold.getBuyerWallet().getUser().getFullName(),
                hold.getSellerWallet().getWalletReference(),
                hold.getSellerWallet().getUser().getFullName(),
                hold.getAmount(),
                hold.getCurrency(),
                hold.getStatus(),
                hold.getDescription(),
                hold.getCreatedAt(),
                hold.getUpdatedAt()
        );
    }
}
