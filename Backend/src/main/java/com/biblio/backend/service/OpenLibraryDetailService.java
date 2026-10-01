package com.biblio.backend.service;

import com.biblio.backend.dto.EditionsResponse;
import com.biblio.backend.dto.WorkDoc;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interroge les endpoints de detail d'Open Library, absents de search.json.
 *
 * search.json fournit titre, auteur, annee et couverture. Il ne fournit ni
 * description, ni langue, ni nombre de pages, ni date de publication : ces
 * informations viennent de l'oeuvre (works) et de ses editions.
 *
 * Comme pour le catalogue, tout est mis en cache : l'API met 5 a 10 secondes
 * a repondre, et ouvrir deux fois la meme page de detail ne doit pas
 * redemander deux fois les memes donnees.
 */
@Service
public class OpenLibraryDetailService {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryDetailService.class);

    private static final String WORKS_PATH = "/works/%s.json";
    private static final String EDITIONS_PATH = "/works/%s/editions.json";

    /** Champs demandes a l'oeuvre : description et sujets (genres). */
    private static final String WORK_FIELDS = "description,subjects,authors,covers,first_publish_date";

    /** Nombre d'editions inspectees pour trouver la mieux renseignee. */
    private static final int EDITIONS_EXAMINEES = 8;

    private final RestClient restClient;
    private final Map<String, Entry<WorkDoc>> cacheOeuvres = new ConcurrentHashMap<>();
    private final Map<String, Entry<EditionsResponse.EditionDoc>> cacheEditions = new ConcurrentHashMap<>();
    private final Map<String, Entry<String>> cacheAuteurs = new ConcurrentHashMap<>();
    private final Duration dureeDeVie;

    public OpenLibraryDetailService(
            RestClient.Builder restClientBuilder,
            @Value("${openlibrary.base-url:https://openlibrary.org}") String baseUrl,
            @Value("${catalogue.cache-duree-secondes:300}") long dureeSecondes) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.dureeDeVie = Duration.ofSeconds(dureeSecondes);
    }

    /**
     * Description et genres d'une oeuvre.
     *
     * @param cle identifiant Open Library, tel que OL82563W
     */
    public WorkDoc oeuvre(String cle) {
        String normalisee = normaliser(cle);
        if (normalisee == null) {
            return null;
        }
        Entry<WorkDoc> entree = cacheOeuvres.get(normalisee);
        if (entree != null && !entree.perime()) {
            return entree.valeur();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fields", WORK_FIELDS);

        try {
            WorkDoc doc = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(WORKS_PATH.formatted(normalisee))
                            .queryParams(params)
                            .build(false))
                    .retrieve()
                    .body(WorkDoc.class);
            cacheOeuvres.put(normalisee, new Entry<>(doc, Instant.now().plus(dureeDeVie)));
            return doc;
        } catch (HttpClientErrorException.NotFound e) {
            // L'ouvrage n'existe pas chez Open Library : c'est une 404, pas une
            // panne. On leve une exception explicitement pour que le controleur
            // reponde "introuvable" au lieu de "service indisponible".
            throw new OuvrageIntrouvableException(normalisee);
        } catch (RestClientException e) {
            throw new OpenLibraryException(
                    "Impossible de recuperer la fiche de l'ouvrage " + normalisee, e);
        }
    }

    /**
     * Edition la mieux renseignee d'une oeuvre.
     *
     * Les editions sont tres inegalement remplies : sur Harry Potter, 2 editions
     * sur 3 n'ont ni langue ni nombre de pages. On retient donc celle qui
     * maximise le nombre de champs disponibles, pour afficher le plus
     * d'informations possible.
     *
     * @return l'edition retenue, ou null si l'oeuvre n'a aucune edition exploitable
     */
    public EditionsResponse.EditionDoc edition(String cle) {
        String normalisee = normaliser(cle);
        if (normalisee == null) {
            return null;
        }
        Entry<EditionsResponse.EditionDoc> entree = cacheEditions.get(normalisee);
        if (entree != null && !entree.perime()) {
            return entree.valeur();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("limit", String.valueOf(EDITIONS_EXAMINEES));

        EditionsResponse.EditionDoc meilleure;
        try {
            EditionsResponse reponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(EDITIONS_PATH.formatted(normalisee))
                            .queryParams(params)
                            .build(false))
                    .retrieve()
                    .body(EditionsResponse.class);

            meilleure = meilleureEdition(
                    reponse == null || reponse.entries() == null ? List.<EditionsResponse.EditionDoc>of() : reponse.entries());
        } catch (RestClientException e) {
            throw new OpenLibraryException(
                    "Impossible de recuperer les editions de l'ouvrage " + normalisee, e);
        }

        cacheEditions.put(normalisee, new Entry<>(meilleure, Instant.now().plus(dureeDeVie)));
        return meilleure;
    }

    /**
     * Nom de l'auteur principal d'une oeuvre.
     *
     * L'oeuvre ne contient que la cle (/authors/OL26320A), pas le nom : un
     * appel supplementaire est donc necessaire lorsqu'aucune donnee locale
     * n'est disponible. Mise en cache comme le reste.
     *
     * @return le nom de l'auteur, ou null s'il est introuvable
     */
    public String auteur(String oeuvreCle, String auteurCle) {
        String cle = normaliser(oeuvreCle);
        if (cle == null) {
            return null;
        }
        Entry<String> entree = cacheAuteurs.get(cle);
        if (entree != null && !entree.perime()) {
            return entree.valeur();
        }
        if (auteurCle == null || auteurCle.isBlank()) {
            return null;
        }

        // L'identifiant peut venir sous la forme "/authors/OL1425869A" ou
        // "OL1425869A" : on garde le dernier segment dans les deux cas.
        // Le suffixe ".json" est obligatoire, sans lui l'API repond par une
        // redirection 303 que RestClient ne suit pas et l'appel echoue, ce qui
        // laissait l'auteur affiche « inconnu ».
        String cleAuteur = dernierSegment(auteurCle);
        if (cleAuteur == null) {
            return null;
        }
        String cheminAuteur = "/authors/" + cleAuteur + ".json";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fields", "name");

        try {
            AuteurDoc doc = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(cheminAuteur).queryParams(params).build(false))
                    .retrieve()
                    .body(AuteurDoc.class);
            String nom = doc == null || doc.name() == null ? null : doc.name().trim();
            if (nom == null || nom.isEmpty()) {
                return null;
            }
            cacheAuteurs.put(cle, new Entry<>(nom, Instant.now().plus(dureeDeVie)));
            return nom;
        } catch (RestClientException e) {
            // L'auteur n'est pas une information bloquante : on degrade plutot
            // que de faire echouer toute la page de detail.
            log.warn("Auteur {} indisponible : {}", auteurCle, e.getMessage());
            return null;
        }
    }

    /** Reponse de /authors/{key}.json */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuteurDoc(String name) {
    }

    /**
     * Choisit l'edition la plus complete : langue, puis nombre de pages, puis
     * date de publication, puis ISBN. L'ordre reflete ce que l'utilisateur lit
     * en premier sur la page de detail.
     */
    private EditionsResponse.EditionDoc meilleureEdition(List<EditionsResponse.EditionDoc> editions) {
        EditionsResponse.EditionDoc meilleure = null;
        int meilleurScore = -1;

        for (EditionsResponse.EditionDoc edition : editions) {
            if (edition == null) {
                continue;
            }
            int score = score(edition);
            if (score > meilleurScore) {
                meilleurScore = score;
                meilleure = edition;
            }
        }
        return meilleure;
    }

    private int score(EditionsResponse.EditionDoc edition) {
        int score = 0;

        // Un ouvrage a souvent des editions dans plusieurs langues. Le score
        // "langue + pages" legerait le classement vers une edition portugaise
        // pour The Hobbit : la note de langue est donc decalee, pour que
        // l'anglais l'emporte a champs equivalents.
        String langue = Langues.premierCode(edition.languages());
        if (langue != null) {
            score += 10;
            if ("eng".equalsIgnoreCase(langue)) {
                score += 8;
            }
        }
        if (edition.numberOfPages() != null) {
            score += 2;
        }
        if (edition.publishDate() != null && !edition.publishDate().isBlank()) {
            score += 1;
        }
        if (!isbins(edition).isEmpty()) {
            score += 1;
        }
        return score;
    }

    /** ISBN, 13 chiffres preferred a 10 chiffres. */
    List<String> isbins(EditionsResponse.EditionDoc edition) {
        if (edition == null) {
            return List.of();
        }
        List<String> source = edition.isbn13() != null && !edition.isbn13().isEmpty()
                ? edition.isbn13()
                : edition.isbn10();
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(3)
                .toList();
    }

    /**
     * Extrait l'identifiant d'une reference Open Library.
     *
     * La reference stockee en base et celle de l'URL ne se presentent pas de la
     * meme facon ("/works/OL82563W" contre "OL82563W") : on accepte les deux.
     */
    /**
     * Dernier segment d'une cle Open Library, sans son extension.
     *
     * "/authors/OL1425869A.json" et "OL1425869A" donnent tous deux
     * "OL1425869A". Retourne null si rien d'exploitable.
     */
    static String dernierSegment(String reference) {
        if (reference == null) {
            return null;
        }
        String propre = reference.trim();
        if (propre.endsWith(".json")) {
            propre = propre.substring(0, propre.length() - ".json".length());
        }
        int slash = propre.lastIndexOf('/');
        if (slash >= 0) {
            propre = propre.substring(slash + 1);
        }
        propre = propre.trim();
        return propre.isEmpty() ? null : propre;
    }

    static String normaliser(String reference) {
        if (reference == null) {
            return null;
        }
        String propre = reference.trim();
        if (propre.isEmpty()) {
            return null;
        }
        int works = propre.indexOf("/works/");
        if (works >= 0) {
            propre = propre.substring(works + "/works/".length());
        }
        int slash = propre.indexOf('/');
        if (slash >= 0) {
            propre = propre.substring(0, slash);
        }
        if (propre.endsWith(".json")) {
            propre = propre.substring(0, propre.length() - ".json".length());
        }
        return propre.isEmpty() ? null : propre;
    }

    private record Entry<T>(T valeur, Instant expireLe) {

        boolean perime() {
            return Instant.now().isAfter(expireLe);
        }
    }
}
