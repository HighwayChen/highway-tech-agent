package com.highway.agent.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    private String type;
    private Object data;

    public static SseEvent references(Object refs) {
        return SseEvent.builder().type("references").data(refs).build();
    }

    public static SseEvent suggested(Object questions) {
        return SseEvent.builder().type("suggested").data(questions).build();
    }

    public static SseEvent done() {
        return SseEvent.builder().type("done").data("").build();
    }
}
