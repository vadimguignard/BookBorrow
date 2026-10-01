package com.biblio.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Corps du POST /api/livres/reservations. Dates au format AAAA-MM-JJ, bornes incluses.
 * Le client est l'utilisateur authentifie (jeton).
 */
public record ReservationDemandeDto(
        @NotBlank String reference,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin
) {
}
