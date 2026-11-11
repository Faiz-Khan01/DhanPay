package com.dhanpay.service;

import com.dhanpay.dto.beneficiary.BeneficiaryDto;
import com.dhanpay.dto.beneficiary.CreateBeneficiaryRequestDto;
import com.dhanpay.entity.Beneficiary;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.repository.BeneficiaryRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Manages customer-saved P2P wallet beneficiaries.
 */
@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AuditService auditService;

    public BeneficiaryService(
            BeneficiaryRepository beneficiaryRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            AuditService auditService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryDto> listBeneficiaries(UserPrincipal principal) {
        return beneficiaryRepository.findAllByOwnerWithTarget(principal.getId()).stream()
                .map(BeneficiaryService::toDto)
                .toList();
    }

    @Transactional
    public BeneficiaryDto addBeneficiary(
            UserPrincipal principal,
            CreateBeneficiaryRequestDto request,
            HttpServletRequest httpRequest) {
        String normalizedRef = request.walletReference().trim().toUpperCase();
        String trimmedNickname = request.nickname().trim();

        User ownerUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Authenticated user not found"));

        Wallet ownerWallet = walletRepository.findByUserId(ownerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Owner wallet not found"));

        Wallet targetWallet = walletRepository.findByWalletReference(normalizedRef)
                .orElseThrow(() -> new ResourceNotFoundException("TARGET_WALLET_NOT_FOUND", "Target wallet reference not found: " + normalizedRef));

        if (Objects.equals(ownerWallet.getId(), targetWallet.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_BENEFICIARY_PROHIBITED", "Cannot save your own wallet as a beneficiary");
        }

        if (beneficiaryRepository.existsByOwnerUserIdAndTargetWalletId(ownerUser.getId(), targetWallet.getId())) {
            throw new ConflictException("BENEFICIARY_EXISTS", "This wallet reference is already in your contacts");
        }

        Beneficiary saved = beneficiaryRepository.save(new Beneficiary(ownerUser, targetWallet, trimmedNickname));

        String details = String.format("{\"wallet\":\"%s\",\"nickname\":\"%s\"}",
                targetWallet.getWalletReference(), trimmedNickname.replace("\"", "'"));
        auditService.logAction(ownerUser.getId(), "BENEFICIARY_ADDED", "BENEFICIARY", String.valueOf(saved.getId()), details, httpRequest);

        return toDto(saved);
    }

    @Transactional
    public void removeBeneficiary(UserPrincipal principal, Long beneficiaryId, HttpServletRequest httpRequest) {
        Beneficiary beneficiary = beneficiaryRepository.findByIdAndOwnerUserId(beneficiaryId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BENEFICIARY_NOT_FOUND", "Saved beneficiary not found"));

        String targetRef = beneficiary.getTargetWallet().getWalletReference();
        beneficiaryRepository.delete(beneficiary);

        auditService.logAction(
                principal.getId(),
                "BENEFICIARY_REMOVED",
                "BENEFICIARY",
                String.valueOf(beneficiaryId),
                "{\"wallet\":\"" + targetRef + "\"}",
                httpRequest
        );
    }

    private static BeneficiaryDto toDto(Beneficiary entity) {
        Wallet target = entity.getTargetWallet();
        return new BeneficiaryDto(
                entity.getId(),
                entity.getNickname(),
                target.getWalletReference(),
                target.getUser().getFullName(),
                target.getStatus(),
                entity.getCreatedAt()
        );
    }
}
