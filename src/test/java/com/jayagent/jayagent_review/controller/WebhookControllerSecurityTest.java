package com.jayagent.jayagent_review.controller;


import com.jayagent.jayagent_review.config.JayAgentProperties;
import com.jayagent.jayagent_review.service.WebhookSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class WebhookControllerSecurityTest {

    @Test
    void strictModeRejectsGitHubWebhookWhenNoSecretIsConfigured() {
        WebhookSecurityService security = securityWithEmptySecrets();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-GitHub-Delivery", "delivery-1");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> security.validateGitHub("{}", request));

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void strictModeRejectsGitLabWebhookWhenNoTokenIsConfigured() {
        WebhookSecurityService security = securityWithEmptySecrets();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Gitlab-Event-UUID", "event-1");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> security.validateGitLab("{}", request));

        assertEquals(401, exception.getStatusCode().value());
    }

    private WebhookSecurityService securityWithEmptySecrets() {
        JayAgentProperties properties = new JayAgentProperties();
        properties.getWebhook().setStrictMode(true);
        properties.getWebhook().setAllowSharedSecretFallback(true);
        properties.getWebhook().setRequireEventId(true);
        Executor executor = command -> command.run();
        return new WebhookSecurityService(properties, "", "", "");
    }
}
