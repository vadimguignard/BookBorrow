package com.biblio.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Reponse de https://openlibrary.org/works/{key}/editions.json
 *
 * Une oeuvre (works) peut avoir plusieurs editions : ce sont les livres
 * reellement imprimes. Chaque edition porte sa langue, son nombre de pages et
 * sa date de publication, qui varient d'une edition a l'autre. La page de
 * detail affiche l'edition la mieux renseignee.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EditionsResponse(
        List<EditionDoc> entries
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EditionDoc(
            String title,
            @JsonProperty("number_of_pages") Integer numberOfPages,
            @JsonProperty("publish_date") String publishDate,
            @JsonProperty("isbn_13") List<String> isbn13,
            @JsonProperty("isbn_10") List<String> isbn10,
            List<LanguageRef> languages,
            @JsonProperty("publishers") List<String> publishers
    ) {
    }

    /**
     * Reference de langue. Open Library renvoie /languages/eng, et non "English" :
     * c'est a nous de traduire ce code en libelle lisible.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LanguageRef(String key) {
    }
}
