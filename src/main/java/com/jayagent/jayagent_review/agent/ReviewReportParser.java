package com.jayagent.jayagent_review.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReviewReportParser {

    private static final Map<String, String> RISK_ICONS = Map.of(
            "CRITICAL", "[CRITICAL]",
            "HIGH", "[HIGH]",
            "MEDIUM", "[MEDIUM]",
            "LOW", "[LOW]"
    );

    private ReviewReportParser() {
    }

    public static void parse(String result, JayAgentReport report) {
        if (result == null || result.isBlank()) {
            return;
        }

        if (tryParseJson(result, report)) {
            if (report.getSummary() == null || report.getSummary().isBlank()) report.setSummary(defaultSummary(report));
            return;
        }
        List<String> candidateSummaryLines = new ArrayList<>();
        for (String line : result.split("\\R")) {
            String trimmed = normalizeLine(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("SUMMARY|")) {
                String summary = trimmed.substring("SUMMARY|".length()).trim();
                if (!summary.isBlank()) {
                    report.setSummary(summary);
                }
            } else if (trimmed.startsWith("RISK|")) {
                parseRiskLine(trimmed, report);
            } else if (looksLikeSummary(trimmed)) {
                candidateSummaryLines.add(trimmed);
            }
        }

        if (report.getSummary() == null || report.getSummary().isBlank()) {
            report.setSummary(!candidateSummaryLines.isEmpty()
                    ? candidateSummaryLines.get(0)
                    : defaultSummary(report));
        }
    }

    private static boolean tryParseJson(String result, JayAgentReport report) {
        try {
            String candidate = result.trim();
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start < 0 || end <= start) return false;
            JsonNode root = new ObjectMapper().readTree(candidate.substring(start, end + 1));
            if (!root.isObject() || !root.has("summary") || !root.has("risks")) return false;
            report.setSummary(root.path("summary").asText(""));
            if (root.path("risks").isArray()) {
                for (JsonNode node : root.path("risks")) {
                    JayAgentReport.RiskItem item = new JayAgentReport.RiskItem();
                    item.setLevel(node.path("level").asText(""));
                    item.setCategory(node.path("category").asText(""));
                    item.setRuleId(node.path("ruleId").asText(""));
                    item.setLocation(node.path("location").asText(""));
                    item.setTitle(node.path("title").asText(""));
                    item.setDescription(node.path("description").asText(""));
                    item.setSuggestion(node.path("suggestion").asText(""));
                    item.setConfidence(parseConfidence(node.path("confidence").asText("0")));
                    item.setSourceReference(node.path("sourceReference").asText(""));
                    item.setSourceContext(node.path("sourceContext").asText(""));
                    item.setIcon(RISK_ICONS.getOrDefault(normalizeLevel(item.getLevel()), "[INFO]"));
                    report.getRisks().add(item);
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void parseRiskLine(String line, JayAgentReport report) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 6) {
            return;
        }
        JayAgentReport.RiskItem item = new JayAgentReport.RiskItem();
        item.setLevel(parts[1].trim());
        if (parts.length >= 11) {
            item.setCategory(parts[2].trim());
            item.setRuleId(parts[3].trim());
            item.setLocation(parts[4].trim());
            item.setTitle(parts[5].trim());
            item.setDescription(parts[6].trim());
            item.setSuggestion(parts[7].trim());
            item.setConfidence(parseConfidence(parts[8].trim()));
            item.setSourceReference(parts[9].trim());
            item.setSourceContext(parts[10].trim());
        } else {
            item.setLocation(parts[2].trim());
            item.setTitle(parts[3].trim());
            item.setDescription(parts[4].trim());
            item.setSuggestion(parts[5].trim());
        }
        item.setIcon(RISK_ICONS.getOrDefault(normalizeLevel(item.getLevel()), "[INFO]"));
        report.getRisks().add(item);
    }

    private static boolean looksLikeSummary(String line) {
        return line.length() <= 240 && !line.contains("|")
                && !line.startsWith("{") && !line.startsWith("[");
    }

    private static String normalizeLine(String line) {
        return line == null ? "" : line.trim();
    }

    private static String normalizeLevel(String level) {
        return level == null ? "" : level.trim().toUpperCase();
    }

    private static double parseConfidence(String value) {
        try {
            return value == null || value.isBlank()
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static String defaultSummary(JayAgentReport report) {
        return report.isHighRisk() ? "High-risk issues detected." : "No obvious review risk found.";
    }
}
