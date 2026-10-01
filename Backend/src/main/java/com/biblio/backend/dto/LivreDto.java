package com.biblio.backend.dto;

import com.biblio.backend.domain.Statut;

import java.time.LocalDate;

/**
 * Livre affiche sur la page d'accueil.
 *
 * Les champs issus d'Open Library (titre, auteur, annee, couverture) sont
 * separes des champs metier (statut, disponibilite) qui viennent de notre
 * seule base de donnees.
 */
public record LivreDto(
        String reference,
        String titre,
        String auteur,
        Integer anneePublication,
        String coverUrl,
        Statut statut,
        LocalDate dateDisponibilite,
        boolean disponible
) {
}
