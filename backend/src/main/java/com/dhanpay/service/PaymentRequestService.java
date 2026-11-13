package com.dhanpay.service;

import com.dhanpay.dto.request.CreatePaymentRequestDto;
import com.dhanpay.dto.request.PaymentRequestItemDto;
import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.entity.PaymentRequest;
import com.dhanpay.entity.PaymentRequestStatus;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.repository.PaymentRequestRepository;
import com.dhanpay.repository.TransferRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates peer-to-peer payment requests and one-click settlement via {@link TransferService}.
 */
@Service
public class PaymentRequestService {

    private static final int MONETARY_SCALE = 4;
    private static final String REF_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final PaymentRequestRepository paymentRequestRepository;
    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final TransferService transferService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private NotificationStreamService notificationStreamService;

    @Autowired(required = false)
    public void setNotificationStreamService(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    public PaymentRequestService(
            PaymentRequestRepository paymentRequestRepository,
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            TransferService transferService,
            AuditService auditService) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.transferService = transferService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PaymentRequestItemDto> listMyRequests(UserPrincipal principal) {
        Wallet myWallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));

        return paymentRequestRepository.findAllInvolvingWallet(myWallet.getId()).stream()
                .map(pr -> toDto(pr, myWallet.getId()))
                .toList();
    }

    @Transactional
    public PaymentRequestItemDto createRequest(
            UserPrincipal principal,
            CreatePaymentRequestDto request,
            HttpServletRequest httpRequest) {
        Wallet requesterWallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Requester wallet not found"));

        if (requesterWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "REQUESTER_WALLET_INACTIVE", "Requester wallet must be ACTIVE");
        }

        String payerRef = request.payerWalletReference().trim().toUpperCase();
        Wallet payerWallet = walletRepository.findByWalletReference(payerRef)
                .orElseThrow(() -> new ResourceNotFoundException("PAYER_WALLET_NOT_FOUND", "Payer wallet reference not found: " + payerRef));

        if (Objects.equals(requesterWallet.getId(), payerWallet.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_REQUEST_PROHIBITED", "Cannot request funds from your own wallet");
        }

        BigDecimal normalizedAmount = request.amount().setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        String reference = generateUniqueReference();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        PaymentRequest created = paymentRequestRepository.save(
                new PaymentRequest(reference, requesterWallet, payerWallet, normalizedAmount, requesterWallet.getCurrency(), note)
        );

        String details = String.format("{\"requester\":\"%s\",\"payer\":\"%s\",\"amount\":\"%s\"}",
                requesterWallet.getWalletReference(), payerWallet.getWalletReference(), normalizedAmount);
        auditService.logAction(principal.getId(), "PAYMENT_REQUEST_CREATED", "PAYMENT_REQUEST", reference, details, httpRequest);

        if (notificationStreamService != null) {
            notificationStreamService.sendToUser(
                    payerWallet.getUser().getId(),
                    "PAYMENT_REQUEST_RECEIVED",
                    "New Payment Request",
                    requesterWallet.getUser().getFullName() + " requested ₹" + normalizedAmount.setScale(2, RoundingMode.HALF_EVEN),
                    java.util.Map.of("reference", reference, "amount", normalizedAmount)
            );
        }

        PaymentRequest reloaded = paymentRequestRepository.findByReferenceWithDetails(created.getReference())
                .orElse(created);
        return toDto(reloaded, requesterWallet.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TransferResponseDto payRequest(
            UserPrincipal principal,
            String requestReference,
            String idempotencyKey,
            HttpServletRequest httpRequest) {
        PaymentRequest pr = paymentRequestRepository.findByReferenceWithDetails(requestReference.trim())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND", "Payment request not found"));

        Wallet myWallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Payer wallet not found"));

        if (!Objects.equals(pr.getPayerWallet().getId(), myWallet.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_PAYMENT_REQUEST", "Only the designated payer can settle this request");
        }

        if (pr.getStatus() == PaymentRequestStatus.PAID) {
            throw new BusinessException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PAID", "This payment request has already been paid");
        }
        if (pr.getStatus() != PaymentRequestStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING", "Cannot pay a " + pr.getStatus() + " request");
        }

        String desc = pr.getNote() != null && !pr.getNote().isBlank()
                ? pr.getNote()
                : "Settlement for " + pr.getReference();

        TransferResponseDto transferRes = transferService.executeTransfer(
                principal,
                idempotencyKey,
                new TransferRequestDto(pr.getRequesterWallet().getWalletReference(), pr.getAmount(), desc),
                httpRequest
        );

        Transfer settledTransfer = transferRepository.findByReference(transferRes.transferReference())
                .orElse(null);
        pr.setStatus(PaymentRequestStatus.PAID);
        pr.setTransfer(settledTransfer);
        paymentRequestRepository.save(pr);

        auditService.logAction(
                principal.getId(),
                "PAYMENT_REQUEST_PAID",
                "PAYMENT_REQUEST",
                pr.getReference(),
                "{\"transferRef\":\"" + transferRes.transferReference() + "\"}",
                httpRequest
        );

        return transferRes;
    }

    @Transactional
    public PaymentRequestItemDto declineOrCancel(
            UserPrincipal principal,
            String requestReference,
            HttpServletRequest httpRequest) {
        PaymentRequest pr = paymentRequestRepository.findByReferenceWithDetails(requestReference.trim())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND", "Payment request not found"));

        Wallet myWallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found"));

        boolean isPayer = Objects.equals(pr.getPayerWallet().getId(), myWallet.getId());
        boolean isRequester = Objects.equals(pr.getRequesterWallet().getId(), myWallet.getId());
        if (!isPayer && !isRequester) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_PAYMENT_REQUEST", "Not authorized for this payment request");
        }

        if (pr.getStatus() != PaymentRequestStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_NOT_PENDING", "Only PENDING requests can be updated");
        }

        PaymentRequestStatus nextStatus = isPayer ? PaymentRequestStatus.DECLINED : PaymentRequestStatus.CANCELLED;
        pr.setStatus(nextStatus);
        PaymentRequest saved = paymentRequestRepository.save(pr);

        auditService.logAction(
                principal.getId(),
                "PAYMENT_REQUEST_" + nextStatus.name(),
                "PAYMENT_REQUEST",
                pr.getReference(),
                "{}",
                httpRequest
        );

        return toDto(saved, myWallet.getId());
    }

    private static PaymentRequestItemDto toDto(PaymentRequest pr, Long myWalletId) {
        boolean iAmPayer = Objects.equals(pr.getPayerWallet().getId(), myWalletId);
        Wallet counterparty = iAmPayer ? pr.getRequesterWallet() : pr.getPayerWallet();
        return new PaymentRequestItemDto(
                pr.getReference(),
                iAmPayer ? "INCOMING" : "OUTGOING",
                counterparty.getUser().getFullName(),
                counterparty.getWalletReference(),
                pr.getAmount(),
                pr.getCurrency(),
                pr.getStatus(),
                pr.getNote(),
                pr.getTransfer() != null ? pr.getTransfer().getReference() : null,
                pr.getCreatedAt()
        );
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(16);
            sb.append("REQ-").append(System.currentTimeMillis()).append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(REF_ALPHABET.charAt(secureRandom.nextInt(REF_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!paymentRequestRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique payment request reference");
    }
}
