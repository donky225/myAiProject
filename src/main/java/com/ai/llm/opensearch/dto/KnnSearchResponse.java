package com.ai.llm.opensearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KnnSearchResponse(Hits hits) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hits(List<Hit> hits) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(String _id, double _score, Source _source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String id, String title, String content) {}
}