package com.biblio.backend.dto;

import java.time.LocalDate;

/** Une periode deja prise pour un livre (bornes incluses). Anonyme : aucun nom n'est expose. */
public record PeriodeReserveeDto(LocalDate dateDebut, LocalDate dateFin) {
}
