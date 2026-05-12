package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.model.SearchResult;
import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchReference;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.model.VisualReportData;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReporterNode implements NodeAction {

    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String originalQuestion = state.value(ResearchStateKeys.ORIGINAL_QUESTION, String.class).orElse("");

        String planSummary = "";
        Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
        if (planObj != null) {
            ResearchPlan plan = convertToType(planObj, ResearchPlan.class);
            if (plan != null) {
                planSummary = plan.getSummary();
            }
        }

        String extractedContents = buildContentsForReport(state);
        String references = buildReferences(state);

        log.info("Generating final report for: {}", originalQuestion);

        // 通知前端报告生成开始
        LlmStreamingHelper.notifyGeneratingReport();

        String report = LlmStreamingHelper.streamReportCall(chatClient,
                promptTemplate.reporterPrompt(originalQuestion, planSummary, extractedContents, references));

        List<ResearchReference> refList = buildReferenceList(state);

        log.info("Report generated, length={}, references={}", report != null ? report.length() : 0, refList.size());

        // 生成可视化报告数据
        VisualReportData visualData = generateVisualData(originalQuestion, planSummary, extractedContents, references);

        Map<String, Object> result = new HashMap<>();
        result.put(ResearchStateKeys.FINAL_REPORT, report != null ? report : "");
        result.put(ResearchStateKeys.REFERENCES, refList);
        result.put(ResearchStateKeys.VISUAL_REPORT_DATA, visualData);
        return result;
    }

    private String buildContentsForReport(OverAllState state) {
        Object contentsObj = state.value(ResearchStateKeys.EXTRACTED_CONTENTS).orElse(null);
        if (contentsObj == null) {
            return "暂无收集的信息";
        }

        try {
            List<ExtractedContent> contents = objectMapper.convertValue(contentsObj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExtractedContent.class));

            StringBuilder sb = new StringBuilder();
            for (ExtractedContent c : contents) {
                if (c.getContent() != null && !c.getContent().isBlank()) {
                    sb.append("【来源: ").append(c.getUrl()).append("】\n");
                    sb.append(c.getContent()).append("\n\n");
                }
            }
            return sb.isEmpty() ? "暂无有效信息" : sb.toString();
        } catch (Exception e) {
            return "信息解析失败";
        }
    }

    private String buildReferences(OverAllState state) {
        Object resultsObj = state.value(ResearchStateKeys.SEARCH_RESULTS).orElse(null);
        if (resultsObj == null) {
            return "";
        }

        try {
            List<SearchResult> results = objectMapper.convertValue(resultsObj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SearchResult.class));

            Set<String> seenUrls = new HashSet<>();
            StringBuilder sb = new StringBuilder();
            int index = 1;
            for (SearchResult r : results) {
                if (r.getUrl() != null && !r.getUrl().isBlank() && seenUrls.add(r.getUrl())) {
                    sb.append("[").append(index++).append("] ")
                            .append(r.getTitle() != null ? r.getTitle() : "无标题")
                            .append(" - ").append(r.getUrl()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private List<ResearchReference> buildReferenceList(OverAllState state) {
        Object resultsObj = state.value(ResearchStateKeys.SEARCH_RESULTS).orElse(null);
        if (resultsObj == null) {
            return List.of();
        }

        try {
            List<SearchResult> results = objectMapper.convertValue(resultsObj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SearchResult.class));

            Set<String> seenUrls = new HashSet<>();
            List<ResearchReference> refs = new ArrayList<>();
            for (SearchResult r : results) {
                if (r.getUrl() != null && !r.getUrl().isBlank() && seenUrls.add(r.getUrl())) {
                    refs.add(ResearchReference.builder()
                            .id(refs.size() + 1)
                            .title(r.getTitle())
                            .url(r.getUrl())
                            .build());
                }
            }
            return refs;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(Object obj, Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        try {
            return objectMapper.convertValue(obj, type);
        } catch (Exception e) {
            log.warn("Failed to convert state value to {}", type.getSimpleName(), e);
            return null;
        }
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private VisualReportData generateVisualData(String originalQuestion, String planSummary,
                                                 String extractedContents, String references) {
        try {
            String llmResponse = LlmStreamingHelper.streamReportCall(chatClient,
                    promptTemplate.visualReporterPrompt(originalQuestion, planSummary, extractedContents, references));

            String json = extractJson(llmResponse);
            VisualReportData data = objectMapper.readValue(json, VisualReportData.class);
            log.info("Visual report data generated: {} charts, {} findings, {} statistics",
                    data.getCharts() != null ? data.getCharts().size() : 0,
                    data.getKeyFindings() != null ? data.getKeyFindings().size() : 0,
                    data.getStatistics() != null ? data.getStatistics().size() : 0);
            return data;
        } catch (Exception e) {
            log.warn("Failed to generate visual report data, falling back to empty", e);
            return VisualReportData.builder().build();
        }
    }
}
