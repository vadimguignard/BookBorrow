package com.biblio.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Demande de reservation incoherente : dates dans le passe, fin avant debut... (HTTP 400). */
public class ReservationInvalideException extends ResponseStatusException {

    public ReservationInvalideException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
