package com.biblio.backend.dto;

import java.time.LocalDate;

/** Informations du profil. Ne contient jamais le mot de passe, meme hache. */
public record UtilisateurDto(
        Long id,
        String username,
        LocalDate dateInscription,
        long nombreReservations
) {
}
