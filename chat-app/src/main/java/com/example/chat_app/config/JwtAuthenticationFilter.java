
        package com.example.chat_app.config;

import com.example.chat_app.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        // Skip authentication for public paths
        if (path.equals("/") || path.equals("/register") || path.equals("/register.html") ||
                path.equals("/login") ||   path.equals("/dashboard") || path.equals("/dashboard.html") || path.equals("/login.html") || path.equals("/index.html") ||
                path.startsWith("/static/") || path.startsWith("/css/") || path.startsWith("/js/") ||
                path.startsWith("/images/") || path.startsWith("/webjars/") || path.startsWith("/uploads/") ||  path.startsWith("/history/")||
                path.equals("/app.js") ||path.equals("/chat/") || path.startsWith("/ws/")||   path.equals("/error") || path.equals("/actuator/metrics/") || path.startsWith("/.well-known/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.validateToken(token, username)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username, null, java.util.Collections.emptyList());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}