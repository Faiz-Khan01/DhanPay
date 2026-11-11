package com.dhanpay.service;

import com.dhanpay.dto.auth.AuthResponse;
import com.dhanpay.dto.auth.ChangePasswordRequestDto;
import com.dhanpay.dto.auth.LoginRequest;
import com.dhanpay.dto.auth.RegisterRequest;
import com.dhanpay.dto.auth.UserSummaryResponse;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerAccountStatus;
import com.dhanpay.ledger.LedgerAccountType;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.JwtTokenProvider;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Handles customer onboarding, wallet/ledger account provisioning, and JWT login workflows.
 */
@Service
public class AuthService {

    private static final String DEFAULT_CURRENCY = "INR";
    private static final String WALLET_PREFIX = "WAL-IND-";
    private static final String LEDGER_PREFIX = "LA-IND-";
    private static final String REFERENCE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int REFERENCE_SUFFIX_LENGTH = 8;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Atomically registers a new customer, provisions their INR wallet, links a CUSTOMER_WALLET
     * ledger account, and issues a signed JWT access token.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(
                request.fullName().trim(),
                normalizedEmail,
                hashedPassword,
                Role.ROLE_CUSTOMER,
                UserStatus.ACTIVE
        );
        User savedUser = userRepository.save(user);

        String walletReference = generateUniqueWalletReference();
        Wallet wallet = new Wallet(
                savedUser,
                walletReference,
                BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),
                DEFAULT_CURRENCY,
                WalletStatus.ACTIVE
        );
        Wallet savedWallet = walletRepository.save(wallet);

        String accountNumber = generateUniqueLedgerAccountNumber();
        LedgerAccount ledgerAccount = new LedgerAccount(
                savedWallet,
                accountNumber,
                LedgerAccountType.CUSTOMER_WALLET,
                DEFAULT_CURRENCY,
                LedgerAccountStatus.ACTIVE
        );
        LedgerAccount savedLedgerAccount = ledgerAccountRepository.save(ledgerAccount);

        UserPrincipal principal = UserPrincipal.from(savedUser);
        String token = jwtTokenProvider.generateToken(principal);

        return buildAuthResponse(token, savedUser, savedWallet, savedLedgerAccount);
    }

    /**
     * Authenticates user credentials via {@link AuthenticationManager}, enforces ACTIVE status,
     * and returns a signed JWT alongside safe user/wallet profile metadata.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Authenticated user not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_INACTIVE",
                    "User account status is " + user.getStatus() + " and cannot sign in"
            );
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for user"));

        LedgerAccount ledgerAccount = ledgerAccountRepository.findByWalletId(wallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        "Ledger account not found for wallet"
                ));

        String token = jwtTokenProvider.generateToken(principal);
        return buildAuthResponse(token, user, wallet, ledgerAccount);
    }

    @Transactional
    public void changePassword(
            UserPrincipal principal,
            ChangePasswordRequestDto request,
            HttpServletRequest httpRequest) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Authenticated user not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        if (auditService != null) {
            auditService.logAction(
                    user.getId(),
                    "USER_PASSWORD_CHANGED",
                    "USER",
                    String.valueOf(user.getId()),
                    "{}",
                    httpRequest
            );
        }
    }

    private AuthResponse buildAuthResponse(
            String token,
            User user,
            Wallet wallet,
            LedgerAccount ledgerAccount) {
        UserSummaryResponse summary = new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                wallet.getWalletReference(),
                wallet.getBalance(),
                wallet.getCurrency(),
                ledgerAccount.getAccountNumber(),
                user.getCreatedAt()
        );
        return new AuthResponse(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationMs(),
                summary
        );
    }

    private String generateUniqueWalletReference() {
        String candidate;
        do {
            candidate = WALLET_PREFIX + randomSuffix();
        } while (walletRepository.existsByWalletReference(candidate));
        return candidate;
    }

    private String generateUniqueLedgerAccountNumber() {
        String candidate;
        do {
            candidate = LEDGER_PREFIX + randomSuffix();
        } while (ledgerAccountRepository.existsByAccountNumber(candidate));
        return candidate;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(REFERENCE_SUFFIX_LENGTH);
        for (int i = 0; i < REFERENCE_SUFFIX_LENGTH; i++) {
            sb.append(REFERENCE_ALPHABET.charAt(secureRandom.nextInt(REFERENCE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
