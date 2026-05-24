package com.highway.agent.interview.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.graph.InterviewQuestionState;
import com.highway.agent.model.interview.agent.GeneratedQuestionResult;
import com.highway.agent.model.interview.agent.InterviewPlanResult;
import com.highway.agent.model.interview.agent.QuestionPlanningResult;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.service.interview.QuestionGenerationAgent;

import java.util.List;
import java.util.Map;

public class RoundQuestionNode implements NodeAction {

    private final QuestionGenerationAgent questionGenerationAgent;
    private final ObjectMapper objectMapper;
    private final int roundNumber;

    public RoundQuestionNode(QuestionGenerationAgent questionGenerationAgent, ObjectMapper objectMapper, int roundNumber) {
        this.questionGenerationAgent = questionGenerationAgent;
        this.objectMapper = objectMapper;
        this.roundNumber = roundNumber;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ResumeAnalysisResult analysisResult = stateValue(state, InterviewQuestionState.RESUME_ANALYSIS_RESULT, ResumeAnalysisResult.class);
        InterviewPlanResult planResult = stateValue(state, InterviewQuestionState.INTERVIEW_PLAN_RESULT, InterviewPlanResult.class);
        QuestionPlanningResult planningResult = stateValue(state, InterviewQuestionState.QUESTION_PLANNING_RESULT, QuestionPlanningResult.class);
        InterviewPlanResult.RoundPlan roundPlan = findRoundPlan(planResult);
        List<QuestionPlanningResult.Item> planningItems = planningResult.items().stream()
                .filter(item -> Integer.valueOf(roundNumber).equals(item.roundNumber()))
                .toList();
        if (planningItems.size() != 2) {
            throw new IllegalStateException("轮次题目规划数量必须为 2: " + roundNumber);
        }
        List<GeneratedQuestionResult> questions = questionGenerationAgent.generateRoundQuestions(analysisResult, planResult, roundPlan, planningItems);
        return Map.of(roundQuestionKey(), questions);
    }

    private InterviewPlanResult.RoundPlan findRoundPlan(InterviewPlanResult planResult) {
        return planResult.rounds().stream()
                .filter(round -> Integer.valueOf(roundNumber).equals(round.roundNumber()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("面试计划缺少轮次: " + roundNumber));
    }

    private String roundQuestionKey() {
        return switch (roundNumber) {
            case 1 -> InterviewQuestionState.ROUND_1_QUESTIONS;
            case 2 -> InterviewQuestionState.ROUND_2_QUESTIONS;
            case 3 -> InterviewQuestionState.ROUND_3_QUESTIONS;
            case 4 -> InterviewQuestionState.ROUND_4_QUESTIONS;
            default -> throw new IllegalStateException("不支持的轮次: " + roundNumber);
        };
    }

    private <T> T stateValue(OverAllState state, String key, Class<T> type) {
        Object value = state.value(key).orElseThrow(() -> new IllegalStateException("Graph state missing: " + key));
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return objectMapper.convertValue(value, type);
    }
}
