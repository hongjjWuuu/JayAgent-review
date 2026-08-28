package com.jayagent.jayagent_review.controller.dto;

public record WebhookAcceptedResponse(
        boolean success, String platform, boolean accepted, boolean duplicate, String message) {
    public static WebhookAcceptedResponse of(String platform, boolean duplicate) {
        return new WebhookAcceptedResponse(true, platform, true, duplicate,
                duplicate ? "duplicate webhook ignored" : "review accepted and scheduled");
    }
}
