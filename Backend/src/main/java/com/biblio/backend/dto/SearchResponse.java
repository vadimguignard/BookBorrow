package com.biblio.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Reponse de https://openlibrary.org/search.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(
        @JsonProperty("numFound") Integer numFound,
        List<SearchDoc> docs
) {
}
