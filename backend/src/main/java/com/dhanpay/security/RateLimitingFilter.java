package com.dhanpay.security;

import com.dhanpay.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter protecting authentication and transfer initiation endpoints.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int LOGIN_LIMIT_PER_MINUTE = 10;
    private static final int TRANSFER_LIMIT_PER_MINUTE = 20;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitingFilter(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    RateLimitingFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        RateLimitPolicy policy = resolvePolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        String bucketKey = policy.prefix() + ":" + resolveCallerKey(request);

        Deque<Long> timestamps = requestWindows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        long retryAfterSeconds = 0L;
        boolean allowed;
        synchronized (timestamps) {
            long cutoff = now - WINDOW_MILLIS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= policy.limit()) {
                allowed = false;
                long oldest = timestamps.peekFirst();
                long waitMillis = Math.max(1_000L, (oldest + WINDOW_MILLIS) - now);
                retryAfterSeconds = (long) Math.ceil(waitMillis / 1000.0d);
            } else {
                timestamps.addLast(now);
                allowed = true;
            }
        }

        if (!allowed) {
            writeTooManyRequests(request, response, retryAfterSeconds, policy.limit());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static RateLimitPolicy resolvePolicy(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) {
            return new RateLimitPolicy("login", LOGIN_LIMIT_PER_MINUTE);
        }
        if ("/api/transfers".equals(path)) {
            return new RateLimitPolicy("transfer", TRANSFER_LIMIT_PER_MINUTE);
        }
        return null;
    }

    private static String resolveCallerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return "user:" + auth.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response,
            long retryAfterSeconds,
            int limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ApiErrorResponse error = new ApiErrorResponse(
                Instant.now(clock),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Limit is " + limit + " per minute. Retry after " + retryAfterSeconds + "s.",
                request.getRequestURI(),
                null
        );
        objectMapper.writeValue(response.getWriter(), error);
    }

    private record RateLimitPolicy(String prefix, int limit) {
    }
}
