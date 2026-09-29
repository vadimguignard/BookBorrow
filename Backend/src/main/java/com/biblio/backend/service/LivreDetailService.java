package com.biblio.backend.service;

import com.biblio.backend.domain.Livre;
import com.biblio.backend.domain.Statut;
import com.biblio.backend.dto.EditionsResponse;
import com.biblio.backend.dto.LivreDto;
import com.biblio.backend.dto.LivreDetailDto;
import com.biblio.backend.dto.WorkDoc;
import com.biblio.backend.repository.LivreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Page de detail d'un livre.
 *
 * Deux sources, frontiere inchangee :
 * - description, langue, pages, date, ISBN, genres -> Open Library
 * - statut et date de disponibilite                  -> notre base
 *
 * L'ouvrage est recherche par sa cle Open Library (works/{cle}.json), qui est
 * la source des informations absentes de search.json. Le titre, l'auteur,
 * l'annee et la couverture proviennent de notre base si le livre y figure, et
 * sont sinon reconstruits depuis l'oeuvre.
 */
@Service
public class LivreDetailService {

    private static final Logger log = LoggerFactory.getLogger(LivreDetailService.class);

    /** Nombre de genres affiches sur la page de detail. */
    private static final int GENRES_AFFICHES = 8;

    private final LivreRepository livreRepository;
    private final OpenLibraryDetailService openLibraryDetailService;
    private final RecommandationFusionService fusionService;

    public LivreDetailService(
            LivreRepository livreRepository,
            OpenLibraryDetailService openLibraryDetailService,
            RecommandationFusionService fusionService) {
        this.livreRepository = livreRepository;
        this.openLibraryDetailService = openLibraryDetailService;
        this.fusionService = fusionService;
    }

    /**
     * Fiche complete d'un livre.
     *
     * @throws OuvrageIntrouvableException si la reference est inconnue
     */
    @Transactional(readOnly = true)
    public LivreDetailDto detail(String reference) {
        String cle = OpenLibraryDetailService.normaliser(reference);
        if (cle == null) {
            throw new OuvrageIntrouvableException(String.valueOf(reference));
        }

        // L'oeuvre est obligatoire : sans elle on n'a ni titre, ni description.
        // OuvrageIntrouvableException est remontee telle quelle pour produire
        // une 404 ; une panne de l'API produit de son cote une 502.
        WorkDoc oeuvre = openLibraryDetailService.oeuvre(cle);
        if (oeuvre == null) {
            throw new OuvrageIntrouvableException(cle);
        }

        EditionsResponse.EditionDoc edition = openLibraryDetailService.edition(cle);
        Livre local = livreRepository.findByReference("/works/" + cle).orElse(null);

        String titre = libelle(oeuvre.title(), local != null ? local.getTitre() : null);
        if (titre == null) {
            throw new OuvrageIntrouvableException(cle);
        }

        // L'auteur vient de notre base quand le livre y est. Sinon l'oeuvre ne
        // fournit qu'une cle (/authors/OL26320A) : un appel de plus est necessaire.
        String auteur = local != null && local.getAuteur() != null
                ? local.getAuteur()
                : openLibraryDetailService.auteur(cle, oeuvre.premiereCleAuteur());

        Integer annee = local != null ? local.getAnneePublication() : null;
        if (annee == null) {
            annee = anneeDepuisDescription(oeuvre);
        }
        String coverUrl = coverUrl(local, oeuvre);

        // Statut et disponibilite : source unique, notre base.
        Statut statut = local != null ? local.getStatut() : Statut.LIBRE;
        LocalDate dateDisponibilite = local != null ? local.getDateDisponibilite() : null;
        if (statut != Statut.RESERVE) {
            dateDisponibilite = null;
        }

        List<String> genres = oeuvre.genresUtiles().stream().limit(GENRES_AFFICHES).toList();

        // On applique exactement le meme tri que les recommandations, pour que
        // les genres affiches soient bien ceux qui ont produit les resultats.
        List<String> genresRecommandation = RecommandationService.genresRecommandation(oeuvre);

        log.info("Detail {} : edition=\"{}\" langue=\"{}\" pages={} genres={}",
                cle, edition != null ? edition.publishDate() : null,
                Langues.libelle(Langues.premierCode(edition != null ? edition.languages() : null)),
                edition != null ? edition.numberOfPages() : null,
                genres.size());

        return new LivreDetailDto(
                "/works/" + cle,
                titre,
                auteur,
                annee,
                coverUrl,
                oeuvre.descriptionLongue(),
                langue(edition),
                edition != null ? edition.numberOfPages() : null,
                edition != null ? nettoyer(edition.publishDate()) : null,
                openLibraryDetailService.isbins(edition),
                genres,
                genresRecommandation,
                statut,
                dateDisponibilite,
                statut == Statut.LIBRE);
    }

    /**
     * Langue de l'edition retenue, en francais.
     *
     * Renvoie null quand le code est absent ou inconnu : l'interface affiche
     * alors « Non renseigne » plutot qu'un code brut comme « eng ».
     */
    private String langue(EditionsResponse.EditionDoc edition) {
        if (edition == null) {
            return null;
        }
        return Langues.libelle(Langues.premierCode(edition.languages()));
    }

    /** Couverture depuis notre base si elle y est, sinon la plus grande de l'oeuvre. */
    private String coverUrl(Livre local, WorkDoc oeuvre) {
        if (local != null && local.getCoverId() != null) {
            return "https://covers.openlibrary.org/b/id/%d-M.jpg".formatted(local.getCoverId());
        }
        return null;
    }

    /** Un champ present chez Open Library prime sur celui de notre base. */
    private String libelle(String openLibrary, String local) {
        if (openLibrary != null && !openLibrary.isBlank()) {
            return openLibrary.trim();
        }
        return local != null && !local.isBlank() ? local.trim() : null;
    }

    /** Nettoie une date de publication parfois stockedee avec des espaces. */
    private String nettoyer(String valeur) {
        if (valeur == null) {
            return null;
        }
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }

    /**
     * Cherche une annee de publication dans le texte de la description.
     *
     * Beaucoup de descriptions commencent par « Originally published in 1954 »
     * ou « First published in 1997 ». C'est un fallback : la valeur de la base
     * reste prioritaire, et l'interface affiche « Non renseigne » sinon. Une
     * annee trouvee dans une phrase n'est pas une donnee fiable, on ne la
     * retourne donc que si elle est isolee par « in », et non au hasard dans
     * le texte.
     */
    private Integer anneeDepuisDescription(WorkDoc oeuvre) {
        String description = oeuvre == null ? null : oeuvre.descriptionLongue();
        if (description == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b(?:in|published in)\\s+(\\d{4})\\b")
                .matcher(description);
        if (!matcher.find()) {
            return null;
        }
        try {
            int annee = Integer.parseInt(matcher.group(1));
            // Une annee aberrante vient d'un bruit de texte, pas d'une reelle date.
            return annee >= 1450 && annee <= 2030 ? annee : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
