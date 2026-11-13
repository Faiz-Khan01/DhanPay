package com.dhanpay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginEndpoint_enforcesTenRequestsPerMinuteAndResetsAfterWindow() throws Exception {
        AtomicLong nowMillis = new AtomicLong(1_700_000_000_000L);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMillis.get());
            }
        };

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RateLimitingFilter filter = new RateLimitingFilter(mapper, mutableClock);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        verify(chain, times(10)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 11th request within 60s must be rejected with 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, chain);

        assertThat(blockedRes.getStatus()).isEqualTo(429);
        assertThat(blockedRes.getHeader("Retry-After")).isNotBlank();
        assertThat(blockedRes.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");

        // Advance clock past 60s window -> allowed again
        nowMillis.addAndGet(61_000L);
        MockHttpServletRequest recoveredReq = new MockHttpServletRequest("POST", "/api/auth/login");
        recoveredReq.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse recoveredRes = new MockHttpServletResponse();
        filter.doFilter(recoveredReq, recoveredRes, chain);
        assertThat(recoveredRes.getStatus()).isEqualTo(200);
    }

    @Test
    void unprotectedEndpoint_passesThroughWithoutRateLimiting() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RateLimitingFilter filter = new RateLimitingFilter(mapper, Clock.systemUTC());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/wallets/me");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        verify(chain, times(30)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
