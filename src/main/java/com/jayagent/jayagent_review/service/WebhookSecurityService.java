package com.jayagent.jayagent_review.service;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Webhook 请求约束、平台认证和共享密钥回退。 */
@Service
public class WebhookSecurityService {
    private final JayAgentProperties properties;
    private final String sharedSecret;
    private final String githubSecret;
    private final String gitlabToken;

    public WebhookSecurityService(JayAgentProperties properties,
                                  @Value("${app.jayagent.webhook.shared-secret:}") String sharedSecret,
                                  @Value("${app.jayagent.webhook.github-secret:}") String githubSecret,
                                  @Value("${app.jayagent.webhook.gitlab-token:}") String gitlabToken) {
        this.properties = properties;
        this.sharedSecret = normalize(sharedSecret);
        this.githubSecret = normalize(githubSecret);
        this.gitlabToken = normalize(gitlabToken);
    }

    public void validateGitHub(String rawBody, HttpServletRequest request) {
        enforceRequestConstraints(rawBody, request);
        requireEventId(request, "X-GitHub-Delivery", "Missing GitHub webhook event id");
        if (!githubSecret.isBlank()) {
            String signature = request.getHeader("X-Hub-Signature-256");
            if (signature == null || !verifyHmacSha256(rawBody, githubSecret, signature)) {
                throw unauthorized("Invalid GitHub webhook signature");
            }
            return;
        }
        requireFallbackOrReject("GitHub webhook secret is required in strict mode", request);
    }

    public void validateGitLab(String rawBody, HttpServletRequest request) {
        enforceRequestConstraints(rawBody, request);
        requireEventId(request, "X-Gitlab-Event-UUID", "Missing GitLab webhook event id");
        if (!gitlabToken.isBlank()) {
            String token = request.getHeader("X-Gitlab-Token");
            if (token == null || !gitlabToken.equals(token.trim())) {
                throw unauthorized("Invalid GitLab webhook token");
            }
            return;
        }
        requireFallbackOrReject("GitLab webhook token is required in strict mode", request);
    }

    private void requireEventId(HttpServletRequest request, String platformHeader, String message) {
        if (properties.getWebhook().isRequireEventId()
                && isBlank(firstNonBlank(request.getHeader(platformHeader), request.getHeader("X-Request-Id")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requireFallbackOrReject(String strictMessage, HttpServletRequest request) {
        if (properties.getWebhook().isStrictMode() && !properties.getWebhook().isAllowSharedSecretFallback()) {
            throw unauthorized(strictMessage);
        }
        if (sharedSecret.isBlank()) {
            if (properties.getWebhook().isStrictMode()) {
                throw unauthorized("Webhook authentication is not configured");
            }
            return;
        }
        String token = request.getHeader("X-JayAgent-Webhook-Token");
        if (token == null || !sharedSecret.equals(token.trim())) {
            throw unauthorized("Invalid webhook token");
        }
    }

    private void enforceRequestConstraints(String rawBody, HttpServletRequest request) {
        if (rawBody != null && rawBody.length() > properties.getWebhook().getMaxBodyLength()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload is too large");
        }
        String contentType = request.getContentType();
        if (contentType != null && !contentType.toLowerCase().contains("application/json")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Webhook content type must be application/json");
        }
    }

    private boolean verifyHmacSha256(String payload, String secret, String expected) {
        if (payload == null || expected == null || !expected.trim().startsWith("sha256=")) return false;
        String actual = hmacSha256Hex(payload, secret);
        String provided = expected.trim().substring("sha256=".length());
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook signature", ex);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!isBlank(value)) return value;
        return "";
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
