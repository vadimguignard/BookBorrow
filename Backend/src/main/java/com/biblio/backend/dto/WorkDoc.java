package com.biblio.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un ouvrage brut renvoye par https://openlibrary.org/works/{key}.json
 *
 * Seule la description demande un traitement particulier : Open Library la
 * renvoie tantôt comme une chaine, tantôt comme un objet { value, type }. Voir
 * {@link #descriptionLongue()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkDoc(
        String title,
        Object description,
        List<String> subjects,
        List<AuthorRef> authors
) {

    /** Reference d'auteur : /authors/OL26320A, sans le nom. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorRef(AuthorNode author) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorNode(String key) {
    }

    /** Premiere cle d'auteur de l'ouvrage, ou null. */
    public String premiereCleAuteur() {
        if (authors == null) {
            return null;
        }
        return authors.stream()
                .filter(Objects::nonNull)
                .map(AuthorRef::author)
                .filter(Objects::nonNull)
                .map(AuthorNode::key)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Description reelle, ou null si l'ouvrage n'en fournit pas.
     *
     * Open Library renvoie la description sous deux formes, sans pouvoir
     * prevoir laquelle : une simple chaine ("The Hobbit is a tale of high
     * adventure...") ou un objet { value, type }. On accepte les deux, sinon
     * la deserialisation echoue et toute la page de detail tombe.
     */
    @SuppressWarnings("unchecked")
    public String descriptionLongue() {
        if (description == null) {
            return null;
        }
        String valeur;
        if (description instanceof String texte) {
            valeur = texte;
        } else if (description instanceof Map<?, ?> objet) {
            Object value = objet.get("value");
            valeur = value instanceof String s ? s : null;
        } else {
            return null;
        }
        if (valeur == null) {
            return null;
        }
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }

    /**
     * Sujets (genres) de l'ouvrage, nettoyes.
     *
     * Open Library y melange de veritables genres ("Magic and Supernatural",
     * "Vampires") et du bruit de catalogage ("series:Harry_Potter",
     * "Accessible book"). On retire le prefixe "series:" : ces entrees
     * designent l'appartenance a une saga, pas un genre, et ne serviraient
     * qu'a ramener des livres de la meme serie.
     */
    public List<String> genresUtiles() {
        if (subjects == null) {
            return List.of();
        }
        return subjects.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .filter(s -> !s.startsWith("series:"))
                .filter(s -> !s.contains("fiction, general"))
                .map(WorkDoc::virerFormeLongue)
                .distinct()
                .toList();
    }

    /**
     * Ramene un sujet a sa forme courte.
     *
     * Open Library melange deux formes d'ecriture : "Vampires" seul, mais aussi
     * "Fiction, Fantasy, Wizards" ou "Dune (Imaginary place), Fiction". La forme
     * longue n'apporte rien et produit des recherches Open Library tres
     * larges, donc on garde le dernier element significant, celui qui nomme
     * precisement le genre.
     */
    private static String virerFormeLongue(String sujet) {
        if (!sujet.contains(",")) {
            return sujet;
        }
        String[] morceaux = sujet.split(",");
        for (int i = morceaux.length - 1; i >= 0; i--) {
            String morceau = morceaux[i].trim();
            if (!morceau.isEmpty()) {
                return morceau;
            }
        }
        return sujet;
    }
}
