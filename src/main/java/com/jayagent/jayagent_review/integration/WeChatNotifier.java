package com.jayagent.jayagent_review.integration;

import com.jayagent.jayagent_review.config.JayAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;


/**
 * Enterprise WeChat notification client.
 */
@Component
public class WeChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeChatNotifier.class);
    private static final String WECHAT_WEBHOOK_KEY = System.getenv("WECHAT_WEBHOOK_KEY");

    private final ExternalApiCallSupport support;
    private final RestClient restClient;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final ObservabilityMetrics metrics;

    public WeChatNotifier(JayAgentProperties properties, ObservabilityMetrics metrics) {
        this.metrics = metrics;
        this.support = new ExternalApiCallSupport(metrics);
        this.restClient = RestClient.builder()
                .requestFactory(support.createRequestFactory(
                        properties.getExternalApi().getConnectTimeoutMillis(),
                        properties.getExternalApi().getReadTimeoutMillis()))
                .build();
        this.maxRetries = Math.max(0, properties.getExternalApi().getMaxRetries());
        this.retryBackoffMillis = Math.max(0L, properties.getExternalApi().getRetryBackoffMillis());
    }

    public void sendMarkdown(String content) {
        if (WECHAT_WEBHOOK_KEY == null || WECHAT_WEBHOOK_KEY.isBlank()) {
            log.warn("WECHAT_WEBHOOK_KEY is not configured; skipping alert.");
            return;
        }

        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + WECHAT_WEBHOOK_KEY;
        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", content)
        );

        try {
            support.executeWithRetry("WeChat", "sendMarkdown", "enterprise-wechat",
                    maxRetries, retryBackoffMillis, () -> {
                        restClient.post().uri(url).body(body).retrieve().toBodilessEntity();
                        return null;
                    }, log);
            metrics.notification(true);
        } catch (RuntimeException ex) {
            metrics.notification(false);
            throw ex;
        }
    }

    public void sendReviewAlert(String projectName, String mrTitle, int score, boolean isHighRisk) {
        StringBuilder sb = new StringBuilder();
        sb.append("## AI Code Review Alert\n\n");
        sb.append("**Project:** ").append(projectName).append("\n\n");
        sb.append("**MR/PR:** ").append(mrTitle).append("\n\n");
        sb.append("**Review Score:** ").append(score).append("/100\n\n");

        if (isHighRisk) {
            sb.append("**High risk detected. Please fix before merging.**\n\n");
        } else {
            sb.append("Code review passed.\n");
        }

        sendMarkdown(sb.toString());
    }
}
