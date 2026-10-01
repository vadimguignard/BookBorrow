package com.biblio.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** La periode demandee chevauche une reservation existante (HTTP 409). */
public class ReservationConflitException extends ResponseStatusException {

    public ReservationConflitException() {
        super(HttpStatus.CONFLICT, "Cette période est déjà réservée.");
    }
}
