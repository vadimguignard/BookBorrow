package com.biblio.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Corps du POST /api/auth/connexion. */
public record ConnexionDto(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire.") String username,
        @NotBlank(message = "Le mot de passe est obligatoire.") String motDePasse
) {
}
