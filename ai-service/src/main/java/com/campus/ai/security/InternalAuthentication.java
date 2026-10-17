package com.campus.ai.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalAuthentication extends OncePerRequestFilter {
    private final String token;
    public InternalAuthentication(@Value("${service.internal-token:}") String token) { this.token = token; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/internal/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String supplied = Objects.toString(request.getHeader("X-Internal-Token"), "");
        if (token.isBlank() || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(token.isBlank() ? 503 : 401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"AI 内部调用认证未配置或不正确\"}"); return;
        }
        if (request.getHeader("X-Actor-Id") != null) {
            try {
                long id = Long.parseLong(request.getHeader("X-Actor-Id")); String role = request.getHeader("X-Actor-Role");
                if (id <= 0 || !Set.of("STUDENT", "MANAGER", "PLATFORM_ADMIN").contains(role)) throw new IllegalArgumentException();
                request.setAttribute("actor", new AiActor(id, role, ids(request.getHeader("X-Actor-Clubs")), ids(request.getHeader("X-Actor-Managed-Clubs"))));
            } catch (IllegalArgumentException | NullPointerException error) {
                response.setStatus(400); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"detail\":\"内部身份格式不正确\"}"); return;
            }
        }
        chain.doFilter(request, response);
    }
    private Set<Long> ids(String header) {
        if (header == null || header.isBlank()) return Set.of();
        String[] values = header.split(","); if (values.length > 200) throw new IllegalArgumentException();
        Set<Long> result = new HashSet<>();
        for (String value : values) { long id = Long.parseLong(value); if (id <= 0) throw new IllegalArgumentException(); result.add(id); }
        return Set.copyOf(result);
    }
}
