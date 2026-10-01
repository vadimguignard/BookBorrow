package com.biblio.backend.dto;

/** Reponse d'une inscription ou d'une connexion reussie. */
public record AuthReponseDto(String token, UtilisateurDto utilisateur) {
}
