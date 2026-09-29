package com.biblio.backend.service;

/**
 * Erreur levee quand l'appel a l'API Open Library echoue (reseau, timeout, HTTP != 200).
 */
public class OpenLibraryException extends RuntimeException {

    public OpenLibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
