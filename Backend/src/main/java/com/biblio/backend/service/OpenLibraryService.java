package com.biblio.backend.service;

import com.biblio.backend.dto.LivreCatalogue;
import com.biblio.backend.dto.SearchDoc;
import com.biblio.backend.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interroge reellement https://openlibrary.org/search.json.
 *
 * Cette integration ne fournit QUE des informations descriptives sur les livres
 * (titre, auteur, annee de publication, couverture). Elle ne determine jamais
 * la disponibilite d'un livre dans notre bibliotheque.
 */
@Service
public class OpenLibraryService {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryService.class);

    private static final String SEARCH_PATH = "/search.json";
    private static final String FIELDS = "title,author_name,first_publish_year,cover_i,key";
    private static final String COVER_URL_TEMPLATE = "https://covers.openlibrary.org/b/id/%d-M.jpg";

    private final RestClient restClient;
    private final int pageSize;
    private final int maxPages;

    public OpenLibraryService(
            RestClient.Builder restClientBuilder,
            @Value("${openlibrary.base-url:https://openlibrary.org}") String baseUrl,
            @Value("${openlibrary.page-size:100}") int pageSize,
            @Value("${openlibrary.max-pages:2}") int maxPages) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.pageSize = pageSize;
        this.maxPages = maxPages;
    }

    /**
     * Recupere un catalogue diversifie depuis Open Library.
     *
     * @param query sujet ou mot-cle recherche ("book", "fantasy", "harry potter"...)
     * @param taille nombre maximum de livres a retourner
     */
    public List<LivreCatalogue> search(String query, int taille) {
        String cleanedQuery = (query == null || query.isBlank()) ? "book" : query.trim();
        int wanted = Math.max(1, Math.min(taille, 200));

        List<SearchDoc> candidates = fetchCandidates(cleanedQuery);
        List<LivreCatalogue> livres = diversify(candidates, wanted);

        log.info("Catalogue Open Library q=\"{}\" : {} candidats bruts -> {} livres diversifies",
                cleanedQuery, candidates.size(), livres.size());
        return livres;
    }

    /**
     * Interroge autant de pages que necessaire pour robuste de la diversification.
     * L'API Open Library met 5 a 10 secondes pour repondre et echoue parfois sur
     * les pages profondes : on Tolere une page en echec et on continue avec les autres.
     */
    private List<SearchDoc> fetchCandidates(String query) {
        List<SearchDoc> candidates = new ArrayList<>();
        boolean auMoinsUnePageLue = false;

        for (int page = 1; page <= maxPages; page++) {
            try {
                SearchResponse response = callSearch(query, page);
                auMoinsUnePageLue = true;
                List<SearchDoc> docs = (response == null || response.docs() == null) ? List.of() : response.docs();
                if (docs.isEmpty()) {
                    break;
                }
                candidates.addAll(docs);
                log.debug("Open Library page {} : {} documents cumules={}", page, docs.size(), candidates.size());
            } catch (OpenLibraryException e) {
                log.warn("Page Open Library {} indisponible pour q=\"{}\" : {}", page, query, e.getMessage());
            }
        }

        // Toutes les pages ont echoue : l'API est injoignable, on leve une erreur.
        if (!auMoinsUnePageLue) {
            throw new OpenLibraryException("Impossible de contacter l'API Open Library (query=\"" + query + "\")", null);
        }
        return candidates;
    }

    private SearchResponse callSearch(String query, int page) {
        // Les parametres sont encodes individuellement puis passes a RestClient sous
        // forme de template : RestClient les encode a nouveau, ce qui doublerait
        // l'encodage des caracteres speciaux (le deux-points de first_publish_year
        // deviendrait %253A et Open Library ne reconnaitrait plus le filtre).
        // build(false) + un encodage par parametre evite ce double encodage.
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        params.add("limit", String.valueOf(pageSize));
        params.add("page", String.valueOf(page));
        params.add("fields", FIELDS);

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParams(params).build())
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientException e) {
            throw new OpenLibraryException("Impossible de contacter l'API Open Library (query=\"" + query + "\")", e);
        }
    }

    /**
     * Diversification du catalogue : on ne garde ni deux livres du meme auteur,
     * ni deux livres de meme titre. Les livres avec couverture sont prioraires.
     */
    private List<LivreCatalogue> diversify(List<SearchDoc> docs, int limit) {
        Set<String> seenAuthors = new LinkedHashSet<>();
        Set<String> seenTitles = new LinkedHashSet<>();
        List<LivreCatalogue> withCover = new ArrayList<>();
        List<LivreCatalogue> withoutCover = new ArrayList<>();

        for (SearchDoc doc : docs) {
            LivreCatalogue livre = toLivreCatalogue(doc);
            if (livre == null) {
                continue;
            }
            String normalizedAuthor = normalize(livre.auteur());
            String normalizedTitle = normalize(livre.titre());
            if (!seenAuthors.add(normalizedAuthor) || !seenTitles.add(normalizedTitle)) {
                continue;
            }
            if (livre.coverUrl() == null) {
                withoutCover.add(livre);
            } else {
                withCover.add(livre);
            }
        }

        List<LivreCatalogue> result = new ArrayList<>(withCover);
        result.addAll(withoutCover);
        return result.size() > limit ? List.copyOf(result.subList(0, limit)) : List.copyOf(result);
    }

    private LivreCatalogue toLivreCatalogue(SearchDoc doc) {
        if (doc == null) {
            return null;
        }
        String titre = trimToNull(doc.title());
        String reference = trimToNull(doc.key());
        String auteur = firstAuthor(doc);
        if (titre == null || reference == null || auteur == null) {
            return null;
        }
        return new LivreCatalogue(
                reference,
                titre,
                auteur,
                doc.firstPublishYear(),
                doc.coverId(),
                doc.coverId() == null ? null : COVER_URL_TEMPLATE.formatted(doc.coverId()));
    }

    private String firstAuthor(SearchDoc doc) {
        if (doc.authorName() == null) {
            return null;
        }
        return doc.authorName().stream()
                .map(this::trimToNull)
                .filter(a -> a != null)
                .findFirst()
                .orElse(null);
    }

    /** "J. K. Rowling" et "J.K. Rowling" doivent compter comme le meme auteur. */
    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
