package com.highway.agent.chat.model;

import lombok.Data;

@Data
public class ChatRequest {

    private String conversationId;

    private String message;
}
