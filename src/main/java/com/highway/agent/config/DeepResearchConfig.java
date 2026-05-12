package com.highway.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "deep-research")
public class DeepResearchConfig {

    private int maxIterations = 3;
    private int maxSubQuestions = 5;
    private int maxDeepSearchRounds = 2;
    private int maxExtractUrls = 3;
    private int executorConcurrency = 3;
    private PlanConfirmation planConfirmation = new PlanConfirmation();

    @Data
    public static class PlanConfirmation {
        private boolean enabled = true;
        private int subQuestionThreshold = 3;
        private int estimatedSearchThreshold = 8;
    }
}
