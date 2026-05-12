package com.highway.agent.controller;

import com.highway.agent.model.DeepResearchRequest;
import com.highway.agent.service.DeepResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/deep-research")
@RequiredArgsConstructor
public class DeepResearchController {

    private final DeepResearchService deepResearchService;

    /**
     * 启动深度研究（SSE 流）
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> startResearch(
            @RequestParam(required = false) String sessionId,
            @RequestParam String message) {
        return deepResearchService.researchStream(sessionId, message);
    }

    /**
     * 恢复暂停的研究（用户回复澄清或确认计划）
     */
    @PostMapping(value = "/{sessionId}/respond", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> respond(
            @PathVariable String sessionId,
            @RequestBody DeepResearchRequest request) {
        return deepResearchService.resumeResearch(sessionId, request.getMessage());
    }

    /**
     * 停止研究
     */
    @PostMapping("/{sessionId}/stop")
    public Map<String, Object> stopResearch(@PathVariable String sessionId) {
        return Map.of("success", true, "message", "Research stopped");
    }
}
