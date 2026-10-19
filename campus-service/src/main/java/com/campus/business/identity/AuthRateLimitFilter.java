package com.campus.business.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

final class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final ObjectMapper mapper;
    AuthRateLimitFilter(AuthRateLimiter limiter, ObjectMapper mapper) { this.limiter = limiter; this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (request.getMethod().equals("POST") && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
            String operation = path.endsWith("/login") ? "login" : "register";
            long retry = limiter.acquire(operation, request.getRemoteAddr(), request.getParameter("username"));
            if (retry > 0) {
                response.setStatus(429); response.setHeader("Retry-After", Long.toString(retry));
                response.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(response.getWriter(), Map.of("detail", "尝试过于频繁，请在 " + retry + " 秒后重试", "retryAfterSeconds", retry));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
