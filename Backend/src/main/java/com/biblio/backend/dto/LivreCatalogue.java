package com.biblio.backend.dto;

/**
 * Livre du catalogue, issu de l'API Open Library.
 *
 * Ces informations sont uniquement DESCRIPTIVES (titre, auteur, annee,
 * couverture). Elles ne disent rien de la disponibilite du livre dans
 * notre bibliotheque : le statut et la date de disponibilite proviennent
 * exclusivement de notre base de donnees.
 */
public record LivreCatalogue(
        String reference,
        String titre,
        String auteur,
        Integer anneePublication,
        Long coverId,
        String coverUrl
) {
}
