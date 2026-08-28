package com.jayagent.jayagent_review.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(JayAgentProperties.class)
public class RiskScoringConfig {

    private static volatile JayAgentProperties.Scoring scoring;

    private final JayAgentProperties properties;

    public RiskScoringConfig(JayAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        scoring = properties.getScoring();
    }

    public static JayAgentProperties.Scoring getScoring() {
        return scoring == null ? new JayAgentProperties.Scoring() : scoring;
    }
}
