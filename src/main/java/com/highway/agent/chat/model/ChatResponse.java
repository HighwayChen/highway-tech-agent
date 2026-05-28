package com.highway.agent.chat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {

    private String answer;

    private List<Reference> references;

    private List<String> suggestedQuestions;

    @Data
    @Builder
    public static class Reference {
        private int id;
        private String title;
        private String url;
    }
}
