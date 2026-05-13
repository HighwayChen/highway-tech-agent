package com.highway.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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

    // Deep Research 事件
    public static SseEvent analyzing(Object data) {
        return SseEvent.builder().type("analyzing").data(data).build();
    }

    public static SseEvent clarifying(Object data) {
        return SseEvent.builder().type("clarifying").data(data).build();
    }

    public static SseEvent planGenerated(Object data) {
        return SseEvent.builder().type("plan_generated").data(data).build();
    }

    public static SseEvent awaitingConfirmation(Object data) {
        return SseEvent.builder().type("awaiting_confirmation").data(data).build();
    }

    public static SseEvent searching(Object data) {
        return SseEvent.builder().type("searching").data(data).build();
    }

    public static SseEvent extracting(Object data) {
        return SseEvent.builder().type("extracting").data(data).build();
    }

    public static SseEvent deepSearching(Object data) {
        return SseEvent.builder().type("deep_searching").data(data).build();
    }

    public static SseEvent critiquing(Object data) {
        return SseEvent.builder().type("critiquing").data(data).build();
    }

    public static SseEvent revisingPlan(Object data) {
        return SseEvent.builder().type("revising_plan").data(data).build();
    }

    public static SseEvent generatingReport(Object data) {
        return SseEvent.builder().type("generating_report").data(data).build();
    }

    public static SseEvent generatingVisual(Object data) {
        return SseEvent.builder().type("generating_visual").data(data).build();
    }

    public static SseEvent reasoningToken(String node, String token) {
        return SseEvent.builder().type("reasoning").data(Map.of("node", node, "token", token)).build();
    }

    public static SseEvent reportToken(String token) {
        return SseEvent.builder().type("report_token").data(Map.of("token", token)).build();
    }

    public static SseEvent visualToken(String token) {
        return SseEvent.builder().type("visual_token").data(Map.of("token", token)).build();
    }

    public static SseEvent complete(Object data) {
        return SseEvent.builder().type("complete").data(data).build();
    }
}
