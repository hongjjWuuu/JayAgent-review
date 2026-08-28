package com.jayagent.jayagent_review.service;

import com.jayagent.jayagent_review.agent.JayAgentReport;
import com.jayagent.jayagent_review.agent.JayAgentScanner;
import com.jayagent.jayagent_review.integration.WeChatNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import com.jayagent.jayagent_review.observability.ObservabilityMetrics;

/**
 * 负责将审查结果编排成业务动作。
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final JayAgentScanner scanner;
    private final WeChatNotifier weChatNotifier;
    private final ReviewHistoryService reviewHistoryService;
    private final ObservabilityMetrics metrics;

    public WebhookService(JayAgentScanner scanner, WeChatNotifier weChatNotifier, ReviewHistoryService reviewHistoryService,
                          ObservabilityMetrics metrics) {
        this.scanner = scanner;
        this.weChatNotifier = weChatNotifier;
        this.reviewHistoryService = reviewHistoryService;
        this.metrics = metrics;
    }

    public JayAgentReport review(String codeDiff, String filePath, String reviewScope) {
        return review(codeDiff, filePath, reviewScope, ReviewHistoryService.ReviewContext.basic("webhook", filePath));
    }

    public JayAgentReport review(String codeDiff, String filePath, String reviewScope, ReviewHistoryService.ReviewContext context) {
        long startedAt = System.nanoTime();
        JayAgentReport report;
        try {
            report = scanner.scan(codeDiff, filePath, reviewScope);
        } catch (RuntimeException ex) {
            metrics.reviewCompleted(context.getPlatform(), false);
            log.error("review_failed source={} filePath={}", context.getPlatform(), filePath, ex);
            throw ex;
        }

        if (report.isHighRisk()) {
            try {
                weChatNotifier.sendReviewAlert(reviewScope, filePath, report.getScore(), true);
            } catch (Exception ex) {
                log.warn("wechat_alert_failed reviewScope={} filePath={}", reviewScope, filePath, ex);
            }
        }

        reviewHistoryService.record(context, report);
        metrics.reviewCompleted(context.getPlatform(), true);
        log.info("review_completed source={} reviewScope={} filePath={} score={} costMs={}",
                reviewScope, filePath, report.getScore(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));

        return report;
    }

    public String buildReviewComment(JayAgentReport report, String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 AI 代码合规审查报告\n\n");
        sb.append("**审查平台：**").append(platform).append("\n\n");
        sb.append("**审查范围：**").append(report.getReviewScope() == null ? "general" : report.getReviewScope()).append("\n\n");
        sb.append("**综合评分：**").append(report.getScore()).append("/100\n\n");

        String summary = report.getSummary();
        if (summary != null && !summary.isBlank()) {
            sb.append("**摘要：**").append(summary).append("\n\n");
        }

        if (report.isHighRisk()) {
            sb.append("⚠️ **本次提交存在高风险项，建议修复后再合并**\n\n");
        }

        if (report.getRisks().isEmpty()) {
            sb.append("✅ 未发现合规风险，代码审查通过。\n");
        } else {
            for (JayAgentReport.RiskItem risk : report.getRisks()) {
                sb.append("---\n");
                sb.append(risk.getIcon()).append(" **[").append(risk.getLevel()).append("]** ");
                sb.append(risk.getTitle()).append("\n\n");
                sb.append("**问题描述：**").append(risk.getDescription()).append("\n\n");
                sb.append("**修复建议：**").append(risk.getSuggestion()).append("\n");
            }
        }
        return sb.toString();
    }
}
