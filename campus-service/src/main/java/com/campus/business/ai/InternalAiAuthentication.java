package com.campus.business.ai;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalAiAuthentication extends OncePerRequestFilter {
    private final String token;
    public InternalAiAuthentication(@Value("${service.internal-token:}") String token) { this.token = token; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/internal/ai/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String supplied = Objects.toString(request.getHeader("X-Internal-Token"), "");
        if (token.isBlank() || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(token.isBlank() ? 503 : 401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"内部调用认证未配置或不正确\"}"); return;
        }
        chain.doFilter(request, response);
    }
}
