package com.highway.agent.interview.model;

import java.util.List;

public record SessionSubmitRequest(List<AnswerSaveRequest.AnswerItem> answers) {
}
