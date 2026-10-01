package com.biblio.backend.dto;

import java.time.LocalDate;

/**
 * Une reservation de l'utilisateur connecte, avec le livre concerne.
 * {@code etat} : A_VENIR, EN_COURS ou TERMINEE (calcule a partir des dates).
 */
public record MesReservationDto(
        Long id,
        String reference,
        String titre,
        String auteur,
        String coverUrl,
        LocalDate dateDebut,
        LocalDate dateFin,
        String etat
) {
}
