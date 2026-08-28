package com.jayagent.jayagent_review.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects non-webhook APIs with a shared API key.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-JayAgent-Api-Key";

    private final boolean enabled;
    private final String apiKey;

    public ApiKeyAuthFilter(@Value("${app.security.api-key-enabled:true}") boolean enabled,
                            @Value("${app.security.api-key:}") String apiKey) {
        this.enabled = enabled;
        this.apiKey = normalize(apiKey);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || path.startsWith("/api/webhook/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (apiKey.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "API_AUTH_NOT_CONFIGURED", "API authentication is enabled but no API key is configured");
            return;
        }

        String provided = normalize(request.getHeader(API_KEY_HEADER));
        if (!constantTimeEquals(apiKey, provided)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "API_KEY_INVALID", "Missing or invalid API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
