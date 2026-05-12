package com.highway.agent.research.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedContent {

    private String url;
    private String title;
    private String content;
    private int subQuestionIndex;
}
