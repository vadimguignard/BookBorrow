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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String genre = oeuvre.genreExact();
        Integer annee = annee(local, oeuvre, edition);
        String coverUrl = coverUrl(local, oeuvre);

        // Statut et disponibilite : source unique, notre base.
        Statut statut = local != null ? local.getStatut() : Statut.LIBRE;
        LocalDate dateDisponibilite = local != null ? local.getDateDisponibilite() : null;
        if (statut != Statut.RESERVE) {
            dateDisponibilite = null;
        }

        // Un seul genre affiche, le plus specifique que l'on reconnaisse.
        List<String> genres = genre == null ? List.of() : List.of(genre);

        // On applique exactement le meme tri que les recommandations, pour que
        // les genres affiches soient bien ceux qui ont produit les resultats.
        List<String> genresRecommandation = RecommandationService.genresRecommandation(oeuvre);

        log.info("Detail {} : edition=\"{}\" langue=\"{}\" pages={} genres={}",
                cle, edition != null ? edition.publishDate() : null,
                Langues.libelle(Langues.premierCode(edition != null ? edition.languages() : null)),
                edition != null ? edition.numberOfPages() : null,
                genre);

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

    /**
     * Couverture : notre base d'abord, sinon les couvertures de l'oeuvre.
     *
     * L'oeuvre liste plusieurs couvertures (portrait, paysage, petite taille).
     * On prend la premiere : c'est celle qu'Open Library affiche par defaut.
     */
    private String coverUrl(Livre local, WorkDoc oeuvre) {
        if (local != null && local.getCoverId() != null) {
            return "https://covers.openlibrary.org/b/id/%d-M.jpg".formatted(local.getCoverId());
        }
        if (oeuvre != null && oeuvre.covers() != null) {
            return oeuvre.covers().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(id -> "https://covers.openlibrary.org/b/id/%d-M.jpg".formatted(id))
                    .orElse(null);
        }
        return null;
    }

    /**
     * Annee de premiere publication.
     *
     * Trois sources, dans cet ordre : notre base, puis le champ
     * first_publish_date de l'oeuvre, puis une annee trouvee dans la date
     * d'edition retenue. On s'arrete a la premiere disponible.
     */
    private Integer annee(Livre local, WorkDoc oeuvre, EditionsResponse.EditionDoc edition) {
        if (local != null && local.getAnneePublication() != null) {
            return local.getAnneePublication();
        }
        Integer depuisOeuvre = extraireAnnee(oeuvre == null ? null : oeuvre.firstPublishDate());
        if (depuisOeuvre != null) {
            return depuisOeuvre;
        }
        return extraireAnnee(edition != null ? edition.publishDate() : null);
    }

    /** Premiere annee a 4 chiffres trouvee dans une date, ou null. */
    private Integer extraireAnnee(String date) {
        if (date == null) {
            return null;
        }
        Matcher matcher = ANNEE.matcher(date);
        if (!matcher.find()) {
            return null;
        }
        try {
            int annee = Integer.parseInt(matcher.group());
            return annee >= 1450 && annee <= 2030 ? annee : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final Pattern ANNEE = Pattern.compile("(\\d{4})");

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
}
