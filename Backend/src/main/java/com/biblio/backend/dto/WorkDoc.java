package com.biblio.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        List<AuthorRef> authors,
        List<Long> covers,
        @JsonProperty("first_publish_date") String firstPublishDate
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
    /**
     * Genre exact du livre, ou null si on n'en connait aucun.
     *
     * On n'affiche qu'un seul genre. Les sujets d'Open Library s'y recouvrent
     * toujours : The Blue Fairy Book se declare a la fois "Fairy tales",
     * "Juvenile Fiction", "Children's fiction" et "Short stories", Harry Potter
     * "Monsters", "Ghosts", "Vampires" et "Fantasy". Tout afficher donne une
     * fiche imprecise ; seul le genre le plus juste compte.
     *
     * Le tri se fait en trois passes sur les sujets, dans l'ordre ou Open
     * Library les classe :
     *
     * 1. les genres principaux, ce qui nomme vraiment l'ecriture
     *    ("fantasy", "romance", "historical fiction") ;
     * 2. les themes, seulement si aucun genre principal ne ressort
     *    ("guerre", "theatre", "vampires") ;
     * 3. les genres larges, en dernier recours ("fiction", "jeunesse").
     *
     * Sans ce decoupage, Harry Potter sortait "Monstres" parce que le sujet
     * "Monsters" arrive troisieme, et Pride and Prejudice sortait "Theatre"
     * parce que "Drama" fait partie de sa premiere ligne de sujets. Un theme
     * ne doit jamais l'emporter sur le genre.
     */
    public String genreExact() {
        if (subjects == null) {
            return null;
        }
        String theme = null;
        String large = null;
        for (String sujet : subjects) {
            if (sujet == null || sujet.isBlank()) {
                continue;
            }
            String normalise = virerFormeLongue(sujet).toLowerCase(Locale.ROOT);
            String principal = premierQuiCorrespond(normalise, GENRES_PRINCIPAUX);
            if (principal != null) {
                return principal;
            }
            if (theme == null) {
                theme = premierQuiCorrespond(normalise, GENRES_THEMES);
            }
            if (large == null) {
                large = premierQuiCorrespond(normalise, GENRES_LARGES);
            }
        }
        return theme != null ? theme : large;
    }

    /** Premier genre du vocabulaire trouve dans ce sujet, ou null. */
    private static String premierQuiCorrespond(String sujet, Map<String, String> vocabulaire) {
        for (Map.Entry<String, String> genre : vocabulaire.entrySet()) {
            if (correspondA(sujet, genre.getKey())) {
                return genre.getValue();
            }
        }
        return null;
    }

    /**
     * Le sujet correspond-il a ce genre ?
     *
     * "fiction" ne doit pas matcher "historical fiction" deux fois, ni
     * "nonfiction" pour "fiction" : on exige un mot entier.
     */
    private static boolean correspondA(String sujet, String genre) {
        if (sujet.equals(genre)) {
            return true;
        }
        return (" " + sujet + " ").contains(" " + genre + " ")
                || sujet.startsWith(genre + ",")
                || sujet.contains(", " + genre + ",")
                || sujet.endsWith(", " + genre);
    }

    /** Genres principaux : ce qui nomme l'ecriture du livre. */
    private static final Map<String, String> GENRES_PRINCIPAUX = new LinkedHashMap<>();

    /** Themes : utilises seulement quand aucun genre principal ne ressort. */
    private static final Map<String, String> GENRES_THEMES = new LinkedHashMap<>();

    /** Genres larges, dernier recours. */
    private static final Map<String, String> GENRES_LARGES = new LinkedHashMap<>();

    static {
        principal("historical fiction", "Historical fiction");
        principal("fairy tales", "Contes et fees");
        principal("folklore", "Folklore");
        principal("science fiction", "Science-fiction");
        principal("dystopia", "Dystopie");
        principal("fantasy", "Fantasy");
        principal("epic", "Epic");
        principal("mystery", "Polar");
        principal("detective", "Polar");
        principal("thriller", "Thriller");
        principal("suspense", "Thriller");
        principal("horror", "Horreur");
        principal("adventure", "Aventure");
        principal("romance", "Romance");
        principal("western", "Western");
        principal("biography", "Biographie");
        principal("autobiography", "Autobiographie");
        principal("memoir", "Memoires");
        principal("poetry", "Poésie");
        principal("cookery", "Cuisine");
        principal("travel", "Voyage");
        principal("christian", "Chretien");

        theme("drama", "Theatre");
        theme("tragedy", "Tragédie");
        theme("war", "Guerre");
        theme("school", "Scolaire");
        theme("vampires", "Vampires");
        theme("magie", "Magie");
        theme("wizards", "Sorcellerie");
        theme("dragons", "Dragons");
        theme("monsters", "Monstres");
        theme("ghost", "Fantastique");
        theme("satire", "Satire");
        theme("comedy", "Comedie");
        theme("humor", "Humour");

        large("fiction", "Fiction");
        large("novel", "Roman");
        large("novels", "Roman");
        large("short stories", "Nouvelles");
        large("juvenile fiction", "Jeunesse");
        large("children's fiction", "Jeunesse");
        large("children's stories", "Jeunesse");
        large("young adult fiction", "Young adult");
        large("art", "Art");
        large("music", "Musique");
        large("essays", "Essais");
        large("history", "Historique");
    }

    private static void principal(String recherche, String libelle) {
        GENRES_PRINCIPAUX.put(recherche, libelle);
    }

    private static void theme(String recherche, String libelle) {
        GENRES_THEMES.put(recherche, libelle);
    }

    private static void large(String recherche, String libelle) {
        GENRES_LARGES.put(recherche, libelle);
    }

    /** Genres bruts, encore bruyants, utilises pour les recommandations. */
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
