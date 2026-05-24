package com.highway.agent.model.interview;

import java.util.List;

public record AnswerSaveRequest(List<AnswerItem> answers) {
    public record AnswerItem(Long questionId, String answer) {
    }
}
