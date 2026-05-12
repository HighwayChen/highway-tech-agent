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
public class ResearchPlan {

    private String summary;
    private List<SubQuestion> subQuestions;
    private int estimatedSearchCount;
}
