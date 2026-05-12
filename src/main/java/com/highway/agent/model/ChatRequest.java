package com.highway.agent.model;

import lombok.Data;

@Data
public class ChatRequest {

    private String conversationId;

    private String message;
}
