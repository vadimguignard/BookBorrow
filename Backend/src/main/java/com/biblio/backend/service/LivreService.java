package com.biblio.backend.service;

import com.biblio.backend.domain.Livre;
import com.biblio.backend.domain.Statut;
import com.biblio.backend.dto.LivreCatalogue;
import com.biblio.backend.dto.LivreDto;
import com.biblio.backend.dto.PageLivresDto;
import com.biblio.backend.repository.LivreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assemble la page d'accueil : catalogue Open Library + donnees metier de notre base.
 *
 * Frontiere entre les deux sources, appliquee strictement :
 * - titre, auteur, annee, couverture  -> Open Library
 * - statut, date de disponibilite     -> notre base de donnees uniquement
 *
 * Un livre absent de notre base est considered LIBRE : il n'a donc pas de
 * date de disponibilite, ce qui est coherent.
 */
@Service
public class LivreService {

    private static final Logger log = LoggerFactory.getLogger(LivreService.class);

    private static final Set<String> TERMES_VIDES = Set.of("", "tous", "toutes", "all");

    /** Requete envoyee a Open Library quand l'utilisateur n'en saisit aucune. */
    private static final String REQUETE_PAR_DEFAUT = "book";

    /** Champ Open Library utilise pour filtrer par annee de premiere publication. */
    private static final String CHAMP_ANNEE = "first_publish_year:";

    private final OpenLibraryService openLibraryService;
    private final CatalogueCache catalogueCache;
    private final LivreRepository livreRepository;
    private final int tailleCatalogue;
    private final int taillePage;

    public LivreService(
            OpenLibraryService openLibraryService,
            CatalogueCache catalogueCache,
            LivreRepository livreRepository,
            @Value("${catalogue.taille:100}") int tailleCatalogue,
            @Value("${livres.taille-page:25}") int taillePage) {
        this.openLibraryService = openLibraryService;
        this.catalogueCache = catalogueCache;
        this.livreRepository = livreRepository;
        this.tailleCatalogue = tailleCatalogue;
        this.taillePage = taillePage;
    }

    /**
     * Page d'accueil : recherche + filtres, puis pagination.
     *
     * @param page numero de page demande, 1 par defaut
     */
    @Transactional(readOnly = true)
    public PageLivresDto pageDaccueil(String q, String auteur, String titre, Integer annee, Integer page) {
        String recherche = nettoyer(q);
        String filtreAuteur = nettoyer(auteur);
        String filtreTitre = nettoyer(titre);

        List<LivreDto> livres = fusionner(catalogue(requeteOpenLibrary(recherche, filtreAuteur, filtreTitre, annee)).stream()
                .filter(livre -> correspond(livre, recherche, filtreAuteur, filtreTitre, annee))
                .toList());

        int pageCourante = (page == null || page < 1) ? 1 : page;
        int totalPages = Math.max(1, (int) Math.ceil((double) livres.size() / taillePage));
        int debut = (pageCourante - 1) * taillePage;
        int fin = Math.min(debut + taillePage, livres.size());
        List<LivreDto> contenu = debut >= livres.size() ? List.of() : livres.subList(debut, fin);

        log.info("Page d'accueil : q=\"{}\" auteur=\"{}\" titre=\"{}\" annee={} -> {} livres, page {}/{}",
                recherche, filtreAuteur, filtreTitre, annee, livres.size(), pageCourante, totalPages);

        return new PageLivresDto(contenu, pageCourante, taillePage, livres.size(), totalPages);
    }

    /**
     * Requete reellement envoyee a Open Library.
     *
     * On n'envoie que le critere le plus specifique (auteur, puis titre, puis
     * recherche), suivi de l'annee : combiner un terme large comme une
     * recherche libre avec un auteur etroit restreindrait trop le catalogue
     * renvoye par l'API.
     *
     * L'API ne renvoie alors que des livres pertinents, au lieu d'un catalogue
     * general dans lequel on filtrerait apres coup. Ce n'est qu'un gain de
     * precision : la combinaison AND reste garantie par {@link #correspond},
     * appliquee a chaque livre.
     *
     * Open Library supporte le champ first_publish_year, ce qui permet de
     * filtrer par annee cote serveur. Sans cela, une annee peu representee
     * dans le catalogue general ne renverrait aucun resultat.
     */
    private String requeteOpenLibrary(String recherche, String filtreAuteur, String filtreTitre, Integer annee) {
        String base;
        if (filtreAuteur != null) {
            base = filtreAuteur;
        } else if (filtreTitre != null) {
            base = filtreTitre;
        } else if (recherche != null) {
            base = recherche;
        } else {
            base = REQUETE_PAR_DEFAUT;
        }

        return annee == null ? base : base + " AND " + CHAMP_ANNEE + annee;
    }

    /**
     * Catalogue Open Library pour une requete donnee, mis en cache.
     * A la premiere visite l'appel peut prendre une dizaine de secondes.
     */
    private List<LivreCatalogue> catalogue(String requete) {
        List<LivreCatalogue> enCache = catalogueCache.get(requete);
        if (enCache != null) {
            return enCache;
        }
        List<LivreCatalogue> livres = openLibraryService.search(requete, tailleCatalogue);
        if (!livres.isEmpty()) {
            catalogueCache.put(requete, livres);
        }
        return livres;
    }

    /**
     * Combinaison des criteres, strictement en AND entre les filtres, avec un OR
     * a l'interieur de la recherche libre (titre OU auteur) :
     *
     * (titre contient q OU auteur contient q)
     * ET auteur LIKE filtreAuteur
     * ET titre  LIKE filtreTitre
     * ET annee = annee
     *
     * Toutes les comparaisons sont insensibles a la casse.
     */
    private boolean correspond(LivreCatalogue livre,
                               String recherche,
                               String filtreAuteur,
                               String filtreTitre,
                               Integer annee) {
        String titre = normaliser(livre.titre());
        String auteur = normaliser(livre.auteur());

        if (recherche != null
                && !titre.contains(recherche)
                && !auteur.contains(recherche)) {
            return false;
        }
        if (filtreAuteur != null && !auteur.contains(filtreAuteur)) {
            return false;
        }
        if (filtreTitre != null && !titre.contains(filtreTitre)) {
            return false;
        }
        return annee == null || annee.equals(livre.anneePublication());
    }

    /**
     * Fusionne le catalogue Open Library avec notre base : le statut et la date de
     * disponibilite proviennent toujours de la base, jamais d'Open Library.
     */
    private List<LivreDto> fusionner(List<LivreCatalogue> catalogue) {
        Map<String, Livre> parReference = livreRepository
                .findByReferenceIn(catalogue.stream().map(LivreCatalogue::reference).toList())
                .stream()
                .collect(Collectors.toMap(Livre::getReference, livre -> livre, (a, b) -> a, HashMap::new));

        return catalogue.stream()
                .map(item -> versDto(item, parReference.get(item.reference())))
                .toList();
    }

    private LivreDto versDto(LivreCatalogue item, Livre local) {
        Statut statut = local != null ? local.getStatut() : Statut.LIBRE;
        LocalDate dateDisponibilite = local != null ? local.getDateDisponibilite() : null;

        // Un livre LIBRE n'a pas de date de disponibilite : elle ne concerne que les reservations.
        if (statut != Statut.RESERVE) {
            dateDisponibilite = null;
        }
        return new LivreDto(
                item.reference(),
                item.titre(),
                item.auteur(),
                item.anneePublication(),
                item.coverUrl(),
                statut,
                dateDisponibilite,
                statut == Statut.LIBRE);
    }

    /** Un filtre a "Toutes" ou "Tous" ne filtre rien. */
    private String nettoyer(String valeur) {
        if (valeur == null) {
            return null;
        }
        String trimmed = valeur.trim();
        if (trimmed.isEmpty() || TERMES_VIDES.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return normaliser(trimmed);
    }

    private String normaliser(String valeur) {
        return valeur.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
