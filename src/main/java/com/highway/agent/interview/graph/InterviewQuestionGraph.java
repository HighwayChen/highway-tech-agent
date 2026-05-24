package com.highway.agent.interview.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.node.QuestionMergeNode;
import com.highway.agent.interview.node.QuestionPlanningNode;
import com.highway.agent.interview.node.RoundQuestionNode;
import com.highway.agent.service.interview.QuestionGenerationAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Component
@RequiredArgsConstructor
public class InterviewQuestionGraph {

    private final QuestionPlanningNode questionPlanningNode;
    private final QuestionGenerationAgent questionGenerationAgent;
    private final QuestionMergeNode questionMergeNode;
    private final ObjectMapper objectMapper;

    public StateGraph buildGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = KeyStrategy.builder()
                .addStrategy(InterviewQuestionState.RESUME_ID, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.SESSION_ID, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.RESUME_ANALYSIS_RESULT, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.INTERVIEW_PLAN_RESULT, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.QUESTION_PLANNING_RESULT, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.ROUND_1_QUESTIONS, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.ROUND_2_QUESTIONS, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.ROUND_3_QUESTIONS, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.ROUND_4_QUESTIONS, KeyStrategy.REPLACE)
                .addStrategy(InterviewQuestionState.MERGED_QUESTIONS, KeyStrategy.REPLACE)
                .build();

        StateGraph graph = new StateGraph("interview-question-generation", keyStrategyFactory);
        graph.addNode("question_planning", AsyncNodeAction.node_async(questionPlanningNode));
        graph.addNode("round_1_question", AsyncNodeAction.node_async(new RoundQuestionNode(questionGenerationAgent, objectMapper, 1)));
        graph.addNode("round_2_question", AsyncNodeAction.node_async(new RoundQuestionNode(questionGenerationAgent, objectMapper, 2)));
        graph.addNode("round_3_question", AsyncNodeAction.node_async(new RoundQuestionNode(questionGenerationAgent, objectMapper, 3)));
        graph.addNode("round_4_question", AsyncNodeAction.node_async(new RoundQuestionNode(questionGenerationAgent, objectMapper, 4)));
        graph.addNode("question_merge", AsyncNodeAction.node_async(questionMergeNode));

        graph.addEdge(START, "question_planning");
        graph.addEdge("question_planning", "round_1_question");
        graph.addEdge("round_1_question", "round_2_question");
        graph.addEdge("round_2_question", "round_3_question");
        graph.addEdge("round_3_question", "round_4_question");
        graph.addEdge("round_4_question", "question_merge");
        graph.addEdge("question_merge", END);
        return graph;
    }
}
