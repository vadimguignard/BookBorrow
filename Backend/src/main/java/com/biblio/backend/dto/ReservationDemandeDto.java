package com.biblio.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Corps du POST /api/livres/reservations. Dates au format AAAA-MM-JJ, bornes incluses. */
public record ReservationDemandeDto(
        @NotBlank String reference,
        @NotBlank @Size(max = 100) String prenom,
        @NotBlank @Size(max = 100) String nom,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin
) {
}
