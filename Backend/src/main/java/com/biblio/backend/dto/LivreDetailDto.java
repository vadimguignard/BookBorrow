package com.biblio.backend.dto;

import com.biblio.backend.domain.Statut;

import java.time.LocalDate;
import java.util.List;

/**
 * Livre affiche sur sa page de detail.
 *
 * Frontiere entre les deux sources, inchangee par rapport a la page d'accueil :
 * - description, langue, pages, date de publication, ISBN -> Open Library
 * - statut LIBRE / RESERVE et date de disponibilite           -> notre base
 *
 * Les champs descriptifs sont null quand Open Library ne les fournit pas : le
 * frontend affiche alors « Non renseigne » plutot qu'une valeur inventee.
 */
public record LivreDetailDto(
        String reference,
        String titre,
        String auteur,
        Integer anneePublication,
        String coverUrl,

        /** Description fournie par Open Library, en anglais. */
        String description,
        /** Libelle de langue en francais, ou null si inconnu. */
        String langue,
        Integer nombrePages,
        /** Date de publication de l'edition retenue, telle que fournie par l'API. */
        String datePublication,
        List<String> isbn,
        /** Genres (sujets Open Library) affiches sur la page. */
        List<String> genres,
        /**
         * Les 3 premiers genres, ceux qui servent de requetes aux
         * recommandations. L'interface les affiche pour expliquer d'ou viennent
         * les livres proposes.
         */
        List<String> genresRecommandation,

        Statut statut,
        LocalDate dateDisponibilite,
        boolean disponible
) {
}
