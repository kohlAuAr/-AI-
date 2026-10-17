package com.campus.ai.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** One immutable model configuration per synchronous request, including multi-round agents. */
@Component
public class ModelSnapshotFilter extends OncePerRequestFilter {
    private final AiSettings settings;
    public ModelSnapshotFilter(AiSettings settings) { this.settings = settings; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        settings.pin();
        try { chain.doFilter(request, response); } finally { settings.unpin(); }
    }
}
