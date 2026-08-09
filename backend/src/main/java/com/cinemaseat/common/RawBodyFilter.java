package com.cinemaseat.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wraps requests for webhook/callback paths so the body can be read multiple times —
 * once for HMAC verification, once for JSON parsing. (addendum B4)
 *
 * Scoped to /api/payments/callback and /api/otp/callback — the two gateway-facing paths.
 */
@Component
@Order(0)
public class RawBodyFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/payments/callback") || uri.startsWith("/api/otp/callback"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new CachedBodyHttpServletRequest(req), res);
    }
}