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
public class CritiqueFeedback {

    private boolean sufficient;
    private double completenessScore;
    private List<String> gaps;
    private String revisionSuggestion;
}
