package com.jayagent.jayagent_review.agent;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ReviewPromptBuilder {

    private ReviewPromptBuilder() {
    }

    public static String buildSearchQuery(String codeDiff, String filePath) {
        StringBuilder query = new StringBuilder("code security review");
        if (filePath != null && !filePath.isBlank()) {
            query.append(" ").append(filePath);
        }
        if (containsAny(codeDiff, "password", "secret", "key", "token")) {
            query.append(" hardcoded secret sensitive data");
        }
        if (containsAny(codeDiff, "SQL", "Statement", "query", "jdbc")) {
            query.append(" SQL injection parameterization");
        }
        if (containsAny(codeDiff, "log", "print", "logger", "console")) {
            query.append(" logging sensitive information exposure");
        }
        return query.toString();
    }

    public static String buildContext(List<Document> documents, double minScore) {
        return documents.stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() >= minScore)
                .map(ReviewPromptBuilder::formatContextDocument)
                .collect(Collectors.joining("\n---\n"));
    }

    public static String buildSystemPrompt(String reviewScope, String context) {
        return "You are a senior code review agent focused on " + reviewScope + ".\n\n"
                + "## Risk categories to inspect:\n"
                + "1. Hardcoded secrets, credentials, or tokens\n"
                + "2. Sensitive information leakage\n"
                + "3. SQL injection risks\n"
                + "4. Open-source license review issues\n"
                + "5. Missing log sanitization\n\n"
                + "## Relevant review references:\n" + context + "\n\n"
                + "## Output format:\n"
                + "Prefer exactly one JSON object, with keys summary and risks. Each risk must contain level, title, description, suggestion, confidence, and may contain category, ruleId, location, sourceReference, sourceContext.\n"
                + "If JSON cannot be produced, use the legacy format below.\n"
                + "You must output a summary line first.\n"
                + "SUMMARY|ONE_SENTENCE_SUMMARY\n\n"
                + "Then output zero or more risk lines.\n"
                + "RISK|LEVEL|CATEGORY|RULE_ID|FILE:LINE|TITLE|DESCRIPTION|SUGGESTION|CONFIDENCE|SOURCE_REFERENCE|SOURCE_CONTEXT\n\n"
                + "LEVEL must be one of: CRITICAL / HIGH / MEDIUM / LOW\n"
                + "If no issue is found, output exactly:\n"
                + "PASS|No obvious review risk found";
    }

    private static String formatContextDocument(Document doc) {
        StringBuilder builder = new StringBuilder(Optional.ofNullable(doc.getText()).orElse(""));
        if (doc.getScore() != null) {
            builder.append("\n[score=").append(doc.getScore()).append("]");
        }
        if (doc.getId() != null) {
            builder.append("\n[source=").append(doc.getId()).append("]");
        }
        if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
            builder.append("\n[metadata=").append(doc.getMetadata()).append("]");
        }
        return builder.toString();
    }

    private static boolean containsAny(String text, String... tokens) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String token : tokens) {
            if (token != null && !token.isBlank() && lower.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
