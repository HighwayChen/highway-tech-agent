package com.highway.agent.interview.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.graph.InterviewQuestionState;
import com.highway.agent.model.interview.agent.InterviewPlanResult;
import com.highway.agent.model.interview.agent.QuestionPlanningResult;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.service.interview.QuestionPlanningAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QuestionPlanningNode implements NodeAction {

    private final QuestionPlanningAgent questionPlanningAgent;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ResumeAnalysisResult analysisResult = stateValue(state, InterviewQuestionState.RESUME_ANALYSIS_RESULT, ResumeAnalysisResult.class);
        InterviewPlanResult planResult = stateValue(state, InterviewQuestionState.INTERVIEW_PLAN_RESULT, InterviewPlanResult.class);
        QuestionPlanningResult planningResult = questionPlanningAgent.planQuestions(analysisResult, planResult);
        return Map.of(InterviewQuestionState.QUESTION_PLANNING_RESULT, planningResult);
    }

    private <T> T stateValue(OverAllState state, String key, Class<T> type) {
        Object value = state.value(key).orElseThrow(() -> new IllegalStateException("Graph state missing: " + key));
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return objectMapper.convertValue(value, type);
    }
}
