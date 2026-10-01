package com.biblio.backend.dto;

import java.time.LocalDate;

/** Reservation enregistree, renvoyee apres un POST reussi. */
public record ReservationDto(
        Long id,
        String reference,
        String prenom,
        String nom,
        LocalDate dateDebut,
        LocalDate dateFin
) {
}
