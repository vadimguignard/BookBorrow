package com.biblio.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regles de validation des comptes : mot de passe (8 car., une majuscule, un chiffre) et username. */
class AuthValidationTest {

    private static void refuseMotDePasse(String mdp) {
        var e = assertThrows(ResponseStatusException.class, () -> AuthService.verifierMotDePasse(mdp));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void motDePasseTropCourtEstRefuse() {
        refuseMotDePasse("Abc123");
    }

    @Test
    void motDePasseSansMajusculeEstRefuse() {
        refuseMotDePasse("motdepasse1");
    }

    @Test
    void motDePasseSansChiffreEstRefuse() {
        refuseMotDePasse("MotDePasse");
    }

    @Test
    void motDePasseConformeEstAccepte() {
        assertDoesNotThrow(() -> AuthService.verifierMotDePasse("MotDePasse1"));
    }

    @Test
    void usernameInvalideEstRefuse() {
        assertThrows(ResponseStatusException.class, () -> AuthService.verifierUsername("ab"));
        assertThrows(ResponseStatusException.class, () -> AuthService.verifierUsername("avec espace"));
    }

    @Test
    void usernameValideEstAccepte() {
        assertDoesNotThrow(() -> AuthService.verifierUsername("ada.lovelace_1"));
    }
}
