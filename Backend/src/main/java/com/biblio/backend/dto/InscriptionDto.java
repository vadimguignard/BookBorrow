package com.biblio.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps du POST /api/auth/inscription.
 *
 * Seule la presence des champs est verifiee ici ; les regles de format (username,
 * robustesse du mot de passe, confirmation identique) sont dans AuthService, pour
 * renvoyer les erreurs dans un ordre previsible.
 */
public record InscriptionDto(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire.") String username,
        @NotBlank(message = "Le mot de passe est obligatoire.") String motDePasse,
        @NotBlank(message = "La confirmation du mot de passe est obligatoire.") String confirmationMotDePasse
) {
}
