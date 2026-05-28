package com.highway.agent.interview.model;

import java.util.List;

public record AnswerSaveRequest(List<AnswerItem> answers) {
    public record AnswerItem(Long questionId, String answer) {
    }
}
