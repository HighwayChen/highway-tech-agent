package com.highway.agent.research.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.config.DeepResearchConfig;
import com.highway.agent.research.model.CritiqueFeedback;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchStateKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchEdgeActions {

    private final DeepResearchConfig config;
    private final ObjectMapper objectMapper;

    // ========== EdgeAction 同步方法（供 StateGraph 条件边使用） ==========

    /**
     * question_analyzer 之后的同步路由
     * 如果用户已经补充过信息，不再二次追问，直接进入 planner
     */
    public String afterQuestionAnalyzerEdge(OverAllState state) {
        String userClarification = state.value(ResearchStateKeys.USER_CLARIFICATION, String.class).orElse("");
        if (userClarification != null && !userClarification.isBlank()) {
            log.info("User already provided clarification, proceeding to planner");
            return "clear";
        }
        String clarity = state.value(ResearchStateKeys.CLARITY_LEVEL, String.class).orElse("clear");
        log.info("Question analyzer result: clarity_level={}", clarity);
        return "needs_clarification".equals(clarity) ? "needs_clarification" : "clear";
    }

    /**
     * planner 之后的同步路由
     * 如果用户已经确认过计划（critic 修订后回到 planner），不再二次确认，直接执行
     */
    public String afterPlannerEdge(OverAllState state) {
        if (!config.getPlanConfirmation().isEnabled()) {
            return "auto_proceed";
        }

        // 用户已确认过计划（通过 plan_review 或 critic 修订后回到 planner），直接执行
        String userClarification = state.value(ResearchStateKeys.USER_CLARIFICATION, String.class).orElse("");
        Integer iterationCount = state.value(ResearchStateKeys.ITERATION_COUNT, Integer.class).orElse(0);
        if (iterationCount > 0 || "confirm".equalsIgnoreCase(userClarification)) {
            log.info("Plan already confirmed (iteration={}), auto proceeding", iterationCount);
            return "auto_proceed";
        }

        Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
        if (planObj == null) {
            return "auto_proceed";
        }

        ResearchPlan plan = convertToType(planObj, ResearchPlan.class);
        if (plan == null || plan.getSubQuestions() == null) {
            return "auto_proceed";
        }
        boolean needsConfirm = plan.getSubQuestions().size() >= config.getPlanConfirmation().getSubQuestionThreshold()
                || plan.getEstimatedSearchCount() >= config.getPlanConfirmation().getEstimatedSearchThreshold();

        log.info("Plan review: subQuestions={}, estimatedSearches={}, needsConfirmation={}",
                plan.getSubQuestions().size(), plan.getEstimatedSearchCount(), needsConfirm);

        return needsConfirm ? "needs_confirmation" : "auto_proceed";
    }

    /**
     * plan_review 之后的同步路由
     */
    public String afterPlanReviewEdge(OverAllState state) {
        String userResponse = state.value(ResearchStateKeys.USER_CLARIFICATION, String.class).orElse("confirm");
        log.info("Plan review: user_response={}", userResponse);
        return "adjust".equalsIgnoreCase(userResponse) ? "adjust" : "confirm";
    }

    /**
     * critic 之后的同步路由
     */
    public String afterCriticEdge(OverAllState state) {
        Boolean sufficient = state.value(ResearchStateKeys.IS_SUFFICIENT, Boolean.class).orElse(false);
        Integer iteration = state.value(ResearchStateKeys.ITERATION_COUNT, Integer.class).orElse(0);

        boolean shouldRevise = !Boolean.TRUE.equals(sufficient) && iteration < config.getMaxIterations();
        log.info("Critic result: sufficient={}, iteration={}, shouldRevise={}", sufficient, iteration, shouldRevise);

        return shouldRevise ? "revise" : "sufficient";
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(Object obj, Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        try {
            return objectMapper.convertValue(obj, type);
        } catch (Exception e) {
            log.warn("Failed to convert state value to {}", type.getSimpleName(), e);
            return null;
        }
    }
}
