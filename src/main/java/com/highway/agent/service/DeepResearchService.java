package com.highway.agent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.memory.MysqlChatMemory;
import com.highway.agent.model.SseEvent;
import com.highway.agent.research.model.CritiqueFeedback;
import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchReference;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.model.SubQuestion;
import com.highway.agent.research.model.VisualReportData;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepResearchService {

    private final CompiledGraph compiledResearchGraph;
    private final MysqlChatMemory chatMemoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * 启动深度研究，返回 SSE 事件流。
     * 图执行到 interruptAfter 节点时暂停，Flux 正常完成。
     */
    public Flux<ServerSentEvent<String>> researchStream(String sessionId, String question) {
        String sId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString() : sessionId;

        chatMemoryRepository.saveAll(sId, List.of(new UserMessage(question)));

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sId)
                .build();

        Map<String, Object> inputs = Map.of(
                ResearchStateKeys.ORIGINAL_QUESTION, question,
                ResearchStateKeys.ITERATION_COUNT, 0,
                ResearchStateKeys.IS_SUFFICIENT, false
        );

        // 创建 token 推理桥接 sink
        Sinks.Many<ServerSentEvent<String>> reasoningSink = Sinks.many().multicast().onBackpressureBuffer();

        // 注册 token 消费者，将 LLM 推理 token 推入 sink
        Consumer<String> tokenConsumer = token -> {
            try {
                reasoningSink.tryEmitNext(toSse(SseEvent.reasoningToken("llm", token)));
            } catch (Exception ignored) {}
        };

        // 注册报告 token 消费者，推送 report_token 事件到前端
        Consumer<String> reportTokenConsumer = token -> {
            try {
                reasoningSink.tryEmitNext(toSse(SseEvent.reportToken(token)));
            } catch (Exception ignored) {}
        };

        // 图事件流：在 boundedElastic 线程中注册 tokenConsumer
        Flux<ServerSentEvent<String>> graphFlux = compiledResearchGraph.stream(inputs, config)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(s -> {
                    LlmStreamingHelper.setTokenConsumer(tokenConsumer);
                    LlmStreamingHelper.setReportTokenConsumer(reportTokenConsumer);
                })
                .map(this::nodeOutputToSse)
                .doFinally(signal -> {
                    LlmStreamingHelper.clearTokenConsumer();
                    LlmStreamingHelper.clearReportTokenConsumer();
                    reasoningSink.tryEmitComplete();
                });

        // 合并图事件流 + 推理 token 流
        return Flux.merge(graphFlux, reasoningSink.asFlux())
                .concatWith(Flux.defer(() -> buildInterruptOrDoneFlux(sId, config)))
                .doFinally(signal -> log.info("Deep research stream ended for session: {}", sId))
                .onErrorResume(e -> {
                    log.error("Deep research stream error", e);
                    return Flux.just(toErrorSse("研究过程中出现错误"), toSse(SseEvent.done()));
                });
    }

    /**
     * 恢复暂停的研究（用户回复后）。
     */
    public Flux<ServerSentEvent<String>> resumeResearch(String sessionId, String userResponse) {
        log.info("Resume research requested: sessionId={}, userResponse={}", sessionId, userResponse);
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        Sinks.Many<ServerSentEvent<String>> reasoningSink = Sinks.many().multicast().onBackpressureBuffer();
        Consumer<String> tokenConsumer = token -> {
            try {
                reasoningSink.tryEmitNext(toSse(SseEvent.reasoningToken("llm", token)));
            } catch (Exception ignored) {}
        };

        Consumer<String> reportTokenConsumer = token -> {
            try {
                reasoningSink.tryEmitNext(toSse(SseEvent.reportToken(token)));
            } catch (Exception ignored) {}
        };

        // updateState 是阻塞调用，必须在 boundedElastic 线程执行
        return Mono.fromCallable(() -> {
                    log.info("Calling updateState for session: {}", sessionId);
                    var result = compiledResearchGraph.updateState(
                            config, Map.of(ResearchStateKeys.USER_CLARIFICATION, userResponse));
                    log.info("updateState completed for session: {}, nextNode={}", sessionId, result.nextNode().orElse("none"));
                    return result;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(updatedConfig -> {
                    Flux<ServerSentEvent<String>> graphFlux = compiledResearchGraph.stream(Map.of(), updatedConfig.withResume())
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnSubscribe(s -> {
                                LlmStreamingHelper.setTokenConsumer(tokenConsumer);
                                LlmStreamingHelper.setReportTokenConsumer(reportTokenConsumer);
                            })
                            .map(this::nodeOutputToSse)
                            .doFinally(signal -> {
                                LlmStreamingHelper.clearTokenConsumer();
                                LlmStreamingHelper.clearReportTokenConsumer();
                                reasoningSink.tryEmitComplete();
                            });

                    return Flux.merge(graphFlux, reasoningSink.asFlux())
                            .concatWith(Flux.defer(() -> buildInterruptOrDoneFlux(sessionId, updatedConfig)))
                            .onErrorResume(e -> {
                                log.error("Resume research error", e);
                                return Flux.just(toErrorSse("恢复研究时出现错误"), toSse(SseEvent.done()));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to resume research", e);
                    return Flux.just(toErrorSse("恢复研究时出现错误"), toSse(SseEvent.done()));
                });
    }

    /**
     * 检查是否遇到中断节点，如果是则推送对应事件。
     * 使用 stateOf() 只读取当前检查点，不会重新执行图。
     */
    private Flux<ServerSentEvent<String>> buildInterruptOrDoneFlux(String sessionId, RunnableConfig config) {
        return Mono.fromCallable(() -> compiledResearchGraph.stateOf(config))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(stateOpt -> {
                    try {
                        if (stateOpt.isPresent()) {
                            OverAllState state = stateOpt.get().state();
                            String clarity = state.value(ResearchStateKeys.CLARITY_LEVEL, String.class).orElse("");

                            // 如果有 final_report，说明已完成
                            String report = state.value(ResearchStateKeys.FINAL_REPORT, String.class).orElse("");
                            if (report != null && !report.isBlank()) {
                                chatMemoryRepository.saveAll(sessionId, List.of(new AssistantMessage(report)));
                                return Flux.empty(); // reporter node 已在 nodeOutputToSse 中处理
                            }

                            // 检查是否在 clarifier 中断（仅当用户未回复时才推送）
                            String userClarification = state.value(ResearchStateKeys.USER_CLARIFICATION, String.class).orElse("");
                            if ("needs_clarification".equals(clarity)
                                    && (userClarification == null || userClarification.isBlank())) {
                                Object questionsObj = state.value(ResearchStateKeys.CLARIFICATION_QUESTIONS).orElse(List.of());
                                List<String> questions = convertToList(questionsObj);
                                return Flux.just(toSse(SseEvent.clarifying(Map.of("questions", questions))));
                            }

                            // 检查是否在 plan_review 中断（仅当用户未确认且未进入迭代时才推送）
                            Integer iterationCount = state.value(ResearchStateKeys.ITERATION_COUNT, Integer.class).orElse(0);
                            boolean alreadyConfirmed = iterationCount > 0 || "confirm".equalsIgnoreCase(userClarification);
                            Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
                            if (planObj != null && !alreadyConfirmed) {
                                ResearchPlan plan = convertToType(planObj, ResearchPlan.class);
                                if (plan != null && plan.getSubQuestions() != null
                                        && plan.getSubQuestions().size() >= 3) {
                                    return Flux.just(toSse(SseEvent.awaitingConfirmation(Map.of("plan", plan))));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("No interrupt state found, stream completed normally", e);
                    }
                    return Flux.empty();
                })
                .onErrorResume(e -> {
                    log.debug("No interrupt state found, stream completed normally", e);
                    return Flux.empty();
                });
    }

    /**
     * 将 NodeOutput 转换为 SSE 事件。
     */
    private ServerSentEvent<String> nodeOutputToSse(NodeOutput output) {
        String node = output.node();
        OverAllState state = output.state();

        try {
            SseEvent event = switch (node) {
                case "question_analyzer" -> SseEvent.analyzing(Map.of(
                        "question", state.value(ResearchStateKeys.ORIGINAL_QUESTION, String.class).orElse("")));
                case "planner" -> SseEvent.planGenerated(Map.of(
                        "plan", state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null)));
                case "executor" -> {
                    Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
                    ResearchPlan plan = planObj != null ? convertToType(planObj, ResearchPlan.class) : null;
                    Object searchResultsObj = state.value(ResearchStateKeys.SEARCH_RESULTS).orElse(List.of());
                    int searchCount = convertToTypeList(searchResultsObj, Object.class).size();
                    Object extractedObj = state.value(ResearchStateKeys.EXTRACTED_CONTENTS).orElse(List.of());
                    int extractCount = convertToTypeList(extractedObj, Object.class).size();
                    Map<String, Object> executorData = new HashMap<>();
                    executorData.put("subQuestionIndex", state.value(ResearchStateKeys.CURRENT_SUB_QUESTION_INDEX, Integer.class).orElse(0));
                    executorData.put("totalSubQuestions", plan != null && plan.getSubQuestions() != null ? plan.getSubQuestions().size() : 0);
                    executorData.put("searchResultCount", searchCount);
                    executorData.put("extractedCount", extractCount);
                    if (plan != null && plan.getSubQuestions() != null) {
                        executorData.put("subQuestions", plan.getSubQuestions().stream()
                                .map(sq -> Map.of("index", sq.getIndex(), "question", sq.getQuestion() != null ? sq.getQuestion() : ""))
                                .toList());
                    }
                    yield SseEvent.searching(executorData);
                }
                case "critic" -> {
                    Object feedbackObj = state.value(ResearchStateKeys.CRITIQUE_FEEDBACK).orElse(null);
                    CritiqueFeedback feedback = feedbackObj != null
                            ? convertToType(feedbackObj, CritiqueFeedback.class) : null;
                    Integer iteration = state.value(ResearchStateKeys.ITERATION_COUNT, Integer.class).orElse(0);
                    yield SseEvent.critiquing(Map.of(
                            "iteration", iteration,
                            "sufficient", feedback != null && feedback.isSufficient(),
                            "completenessScore", feedback != null ? feedback.getCompletenessScore() : 0));
                }
                case "reporter" -> {
                    String report = state.value(ResearchStateKeys.FINAL_REPORT, String.class).orElse("");
                    Object refsObj = state.value(ResearchStateKeys.REFERENCES).orElse(List.of());
                    List<ResearchReference> refs = convertToTypeList(refsObj, ResearchReference.class);
                    Object visualObj = state.value(ResearchStateKeys.VISUAL_REPORT_DATA).orElse(null);
                    VisualReportData visualData = visualObj != null
                            ? convertToType(visualObj, VisualReportData.class) : null;
                    Map<String, Object> data = new HashMap<>();
                    data.put("report", report);
                    data.put("references", refs);
                    data.put("visualData", visualData);
                    yield SseEvent.complete(data);
                }
                case "__END__" -> SseEvent.done();
                default -> SseEvent.builder().type("progress").data(Map.of("node", node)).build();
            };
            return toSse(event);
        } catch (Exception e) {
            log.warn("Failed to convert node output for node: {}", node, e);
            return toSse(SseEvent.builder().type("progress").data(Map.of("node", node)).build());
        }
    }

    private ServerSentEvent<String> toSse(SseEvent event) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data(objectMapper.writeValueAsString(event.getData()))
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data("{}")
                    .build();
        }
    }

    private ServerSentEvent<String> toErrorSse(String text) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"message\":\"" + text.replace("\"", "\\\"") + "\"}")
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(Object obj, Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        try {
            return objectMapper.convertValue(obj, type);
        } catch (Exception e) {
            log.warn("Failed to convert to {}", type.getSimpleName(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> convertToTypeList(Object obj, Class<T> type) {
        try {
            return objectMapper.convertValue(obj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> convertToList(Object obj) {
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        try {
            return objectMapper.convertValue(obj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
