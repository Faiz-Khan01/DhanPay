package com.dhanpay.service;

import com.dhanpay.dto.auth.AuthResponse;
import com.dhanpay.dto.auth.LoginRequest;
import com.dhanpay.dto.auth.RegisterRequest;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerAccountStatus;
import com.dhanpay.ledger.LedgerAccountType;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.JwtTokenProvider;
import com.dhanpay.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_createsUserWalletAndCustomerLedgerAccount() {
        RegisterRequest request = new RegisterRequest("Neha Kapoor", "Neha@Example.com", "StrongPass@123");

        when(userRepository.existsByEmail("neha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass@123")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });
        when(walletRepository.existsByWalletReference(anyString())).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "id", 20L);
            return w;
        });
        when(ledgerAccountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(ledgerAccountRepository.save(any(LedgerAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("neha@example.com");
        assertThat(response.user().role()).isEqualTo(Role.ROLE_CUSTOMER);
        assertThat(response.user().balance()).isEqualByComparingTo("0.0000");
        assertThat(response.user().walletReference()).startsWith("WAL-IND-");
        assertThat(response.user().ledgerAccountNumber()).startsWith("LA-IND-");
    }

    @Test
    void register_throwsConflictWhenEmailExists() {
        when(userRepository.existsByEmail("aarav@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Aarav", "aarav@example.com", "Secret@123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_rejectsInactiveUser() {
        User suspendedUser = new User("Priya", "priya@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.SUSPENDED);
        ReflectionTestUtils.setField(suspendedUser, "id", 3L);
        UserPrincipal principal = UserPrincipal.from(suspendedUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(3L)).thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("priya@example.com", "Customer@1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    void login_returnsTokenAndMetadataForActiveUser() {
        User activeUser = new User("Aarav Sharma", "aarav@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(activeUser, "id", 2L);
        Wallet wallet = new Wallet(activeUser, "WLT-IND-10000002", new BigDecimal("25000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(wallet, "id", 2L);
        LedgerAccount account = new LedgerAccount(wallet, "LA-IND-10000002", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);

        UserPrincipal principal = UserPrincipal.from(activeUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeUser));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(wallet));
        when(ledgerAccountRepository.findByWalletId(2L)).thenReturn(Optional.of(account));
        when(jwtTokenProvider.generateToken(principal)).thenReturn("signed-jwt");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(new LoginRequest("AARAV@example.com", "Customer@1234"));

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.user().walletReference()).isEqualTo("WLT-IND-10000002");
        assertThat(response.user().ledgerAccountNumber()).isEqualTo("LA-IND-10000002");
        assertThat(response.user().balance()).isEqualByComparingTo("25000.0000");
    }
}
