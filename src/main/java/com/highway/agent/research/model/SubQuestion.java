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
public class SubQuestion {

    private int index;
    private String question;
    private String searchStrategy;
    private List<String> searchQueries;
}
