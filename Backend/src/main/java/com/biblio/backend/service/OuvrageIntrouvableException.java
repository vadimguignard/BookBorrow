package com.biblio.backend.service;

/**
 * L'ouvrage demande n'existe pas chez Open Library.
 *
 * Distincte d'{@link OpenLibraryException} : celle-ci signifie que l'API est
 * injoignable (erreur 502 pour l'utilisateur), celle-la que la reference est
 * introuvable (erreur 404). Sans cette distinction, un simple livre absent
 * serait signale comme une panne du service.
 */
public class OuvrageIntrouvableException extends RuntimeException {

    public OuvrageIntrouvableException(String reference) {
        super("Livre introuvable : " + reference);
    }
}
