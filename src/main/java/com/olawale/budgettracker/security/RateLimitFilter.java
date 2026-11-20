package com.olawale.budgettracker.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Brute-force brake for the auth endpoints: token-bucket per client IP.
 * Default 10 requests/minute per IP across /login, /refresh, /register.
 *
 * LIMITATION (known, documented): buckets live in this JVM's memory, so the
 * limit is per-instance and resets on restart. Multi-instance deployments must
 * move this to the gateway (ALB/API Gateway) or a shared store (bucket4j_jcache+Hazelcast/Redis).
 *
 * Deliberately NOT a @Component — registered manually in SecurityConfig.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/register");

    private final long requestsPerMinute;
    private final java.util.concurrent.ConcurrentHashMap<String, Bucket> buckets =
            new java.util.concurrent.ConcurrentHashMap<>();

    public RateLimitFilter(long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // NOTE: behind a proxy, getRemoteAddr() is the proxy's IP — trust X-Forwarded-For
        // only from a proxy you control (server.forward-headers-strategy=framework).
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429); // Too Many Requests
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.getWriter().write(
                "{\"status\":429,\"message\":\"Too many requests. Try again later.\"}");
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
