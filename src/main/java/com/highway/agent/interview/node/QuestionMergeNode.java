package com.highway.agent.interview.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.graph.InterviewQuestionState;
import com.highway.agent.model.interview.agent.GeneratedQuestionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class QuestionMergeNode implements NodeAction {

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<GeneratedQuestionResult> questions = Stream.of(
                        roundQuestions(state, InterviewQuestionState.ROUND_1_QUESTIONS),
                        roundQuestions(state, InterviewQuestionState.ROUND_2_QUESTIONS),
                        roundQuestions(state, InterviewQuestionState.ROUND_3_QUESTIONS),
                        roundQuestions(state, InterviewQuestionState.ROUND_4_QUESTIONS)
                )
                .flatMap(List::stream)
                .sorted(Comparator.comparing(GeneratedQuestionResult::roundNumber)
                        .thenComparing(GeneratedQuestionResult::questionNumber))
                .toList();
        validate(questions);
        return Map.of(InterviewQuestionState.MERGED_QUESTIONS, questions);
    }

    private List<GeneratedQuestionResult> roundQuestions(OverAllState state, String key) {
        Object value = state.value(key).orElseThrow(() -> new IllegalStateException("Graph state missing: " + key));
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private void validate(List<GeneratedQuestionResult> questions) {
        if (questions.size() != 8) {
            throw new IllegalStateException("题目合并后必须正好 8 道");
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (GeneratedQuestionResult question : questions) {
            if (question.roundNumber() == null || question.roundNumber() < 1 || question.roundNumber() > 4) {
                throw new IllegalStateException("题目轮次不合法");
            }
            if (question.questionNumber() == null || question.questionNumber() < 1 || question.questionNumber() > 2) {
                throw new IllegalStateException("题目题号不合法");
            }
            if (!uniqueKeys.add(question.roundNumber() + "-" + question.questionNumber())) {
                throw new IllegalStateException("题目轮次和题号重复");
            }
            if (question.content() == null || question.content().isBlank()) {
                throw new IllegalStateException("题目内容不能为空");
            }
            if (question.scoringPoints() == null || question.scoringPoints().isEmpty()) {
                throw new IllegalStateException("题目评分要点不能为空");
            }
        }
    }
}
