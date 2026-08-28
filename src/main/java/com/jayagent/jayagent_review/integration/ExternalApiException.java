package com.jayagent.jayagent_review.integration;

/**
 * Wraps failures from third-party integrations with endpoint context.
 */
public class ExternalApiException extends RuntimeException {

    private final String system;
    private final String operation;

    public ExternalApiException(String system, String operation, String message, Throwable cause) {
        super(message, cause);
        this.system = system;
        this.operation = operation;
    }

    public String getSystem() {
        return system;
    }

    public String getOperation() {
        return operation;
    }
}
