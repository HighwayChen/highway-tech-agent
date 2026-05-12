package com.highway.agent.research.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisualReportData {

    private String title;
    private String summary;
    private List<KeyFinding> keyFindings;
    private List<StatisticItem> statistics;
    private List<ChartConfig> charts;
    private List<String> conclusions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyFinding {
        private String icon;
        private String title;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticItem {
        private String label;
        private String value;
        private String trend;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartConfig {
        private String id;
        private String title;
        private String chartType;
        private Object option;
    }
}
