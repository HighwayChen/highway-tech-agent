package com.highway.agent.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String title;

    private String url;

    private String content;

    private double score;
}
