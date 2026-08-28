package com.jayagent.jayagent_review.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ApiKeyAuthFilterTest {

    @Test
    void allowsRequestWithConfiguredApiKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(true, "test-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jayagent/history");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsRequestWhenApiKeyIsMissing() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(true, "test-key");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jayagent/scan");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("API_KEY_INVALID"));
        verifyNoInteractions(chain);
    }

    @Test
    void failsClosedWhenAuthenticationIsEnabledWithoutConfiguredKey() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(true, "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("API_AUTH_NOT_CONFIGURED"));
        verifyNoInteractions(chain);
    }

    @Test
    void doesNotInterceptWebhookRequests() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(true, "test-key");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhook/github");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
