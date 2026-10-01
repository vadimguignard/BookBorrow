package com.biblio.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Un document brut renvoye par https://openlibrary.org/search.json
 * (uniquement les champs demandes via le parametre "fields").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchDoc(
        String title,
        @JsonProperty("author_name") List<String> authorName,
        @JsonProperty("first_publish_year") Integer firstPublishYear,
        @JsonProperty("cover_i") Long coverId,
        String key
) {
}
