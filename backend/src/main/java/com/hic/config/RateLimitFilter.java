package com.hic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple IP-based rate limiter using a sliding window.
 * Limits login endpoints to prevent brute-force attacks.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.max-requests-per-minute:60}")
    private int maxRequestsPerMinute;

    @Value("${rate-limit.login-max-per-minute:10}")
    private int loginMaxPerMinute;

    private static final long WINDOW_MS = 60_000L;

    // IP -> [window start ms, count]
    private final Map<String, long[]> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, long[]> loginCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = getClientIp(request);
        String path = request.getRequestURI();
        boolean isLoginEndpoint = path.contains("/auth/login");

        if (isLoginEndpoint && isRateLimited(ip, loginCounts, loginMaxPerMinute)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
            log.warn("Rate limit exceeded for login from IP: {}", ip);
            return;
        }

        if (!isLoginEndpoint && isRateLimited(ip, requestCounts, maxRequestsPerMinute)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please slow down your requests.\"}");
            log.warn("Rate limit exceeded for IP: {} on path: {}", ip, path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip, Map<String, long[]> counts, int maxPerMinute) {
        long now = System.currentTimeMillis();

        counts.compute(ip, (key, val) -> {
            if (val == null || now - val[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            val[1]++;
            return val;
        });

        long[] data = counts.get(ip);
        return data != null && data[1] > maxPerMinute;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Periodically remove expired entries to prevent unbounded map growth.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        evictExpired(requestCounts, now);
        evictExpired(loginCounts, now);
    }

    private void evictExpired(Map<String, long[]> counts, long now) {
        Iterator<Map.Entry<String, long[]>> it = counts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, long[]> entry = it.next();
            if (now - entry.getValue()[0] > WINDOW_MS) {
                it.remove();
            }
        }
    }
}
