package com.dhanpay.security;

import com.dhanpay.config.JwtProperties;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("4d687a59324b706e5837725439714c35624e386d46326a51775a3476635870394b366852336d5738795432664e376251");
        properties.setExpirationMs(3600000L);
        properties.setIssuer("dhanpay-engine");

        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.init();
    }

    @Test
    void generateAndValidateToken_extractsExpectedClaims() {
        User user = new User("Aarav Sharma", "aarav@example.com", "hashed", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 42L);
        UserPrincipal principal = UserPrincipal.from(user);

        String token = jwtTokenProvider.generateToken(principal);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractEmail(token)).isEqualTo("aarav@example.com");
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        User user = new User("Aarav Sharma", "aarav@example.com", "hashed", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 42L);
        String token = jwtTokenProvider.generateToken(UserPrincipal.from(user));

        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
    }
}
