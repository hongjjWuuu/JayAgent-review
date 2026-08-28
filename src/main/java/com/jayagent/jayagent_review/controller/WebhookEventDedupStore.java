package com.jayagent.jayagent_review.controller;

public interface WebhookEventDedupStore {

    boolean markProcessed(String dedupeKey);
}
