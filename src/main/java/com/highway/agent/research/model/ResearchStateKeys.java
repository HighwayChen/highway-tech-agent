package com.highway.agent.research.model;

import com.alibaba.cloud.ai.graph.KeyStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ResearchStateKeys {

    public static final String ORIGINAL_QUESTION = "original_question";
    public static final String CLARITY_LEVEL = "clarity_level";
    public static final String CLARIFICATION_QUESTIONS = "clarification_questions";
    public static final String USER_CLARIFICATION = "user_clarification";
    public static final String RESEARCH_PLAN = "research_plan";
    public static final String CURRENT_SUB_QUESTION_INDEX = "current_sub_question_index";
    public static final String SEARCH_RESULTS = "search_results";
    public static final String EXTRACTED_CONTENTS = "extracted_contents";
    public static final String CRITIQUE_FEEDBACK = "critique_feedback";
    public static final String ITERATION_COUNT = "iteration_count";
    public static final String IS_SUFFICIENT = "is_sufficient";
    public static final String FINAL_REPORT = "final_report";
    public static final String REFERENCES = "references";
    public static final String VISUAL_REPORT_DATA = "visual_report_data";

    private ResearchStateKeys() {}

    public static Map<String, KeyStrategy> keyStrategyMap() {
        Map<String, KeyStrategy> map = new LinkedHashMap<>();
        // REPLACE 策略：标量值
        map.put(ORIGINAL_QUESTION, KeyStrategy.REPLACE);
        map.put(CLARITY_LEVEL, KeyStrategy.REPLACE);
        map.put(USER_CLARIFICATION, KeyStrategy.REPLACE);
        map.put(RESEARCH_PLAN, KeyStrategy.REPLACE);
        map.put(CURRENT_SUB_QUESTION_INDEX, KeyStrategy.REPLACE);
        map.put(CRITIQUE_FEEDBACK, KeyStrategy.REPLACE);
        map.put(ITERATION_COUNT, KeyStrategy.REPLACE);
        map.put(IS_SUFFICIENT, KeyStrategy.REPLACE);
        map.put(FINAL_REPORT, KeyStrategy.REPLACE);
        map.put(REFERENCES, KeyStrategy.REPLACE);
        map.put(VISUAL_REPORT_DATA, KeyStrategy.REPLACE);
        // APPEND 策略：列表值
        map.put(CLARIFICATION_QUESTIONS, KeyStrategy.APPEND);
        map.put(SEARCH_RESULTS, KeyStrategy.APPEND);
        map.put(EXTRACTED_CONTENTS, KeyStrategy.APPEND);
        return map;
    }
}
