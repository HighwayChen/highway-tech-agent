package com.highway.agent.model.interview;

import java.util.List;

public record SessionSubmitRequest(List<AnswerSaveRequest.AnswerItem> answers) {
}
