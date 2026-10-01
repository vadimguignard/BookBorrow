package com.biblio.backend.controller;

import com.biblio.backend.service.OpenLibraryException;
import com.biblio.backend.service.OuvrageIntrouvableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OpenLibraryException.class)
    public ResponseEntity<Map<String, String>> handleUpstream(OpenLibraryException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Un livre absent du catalogue est une 404, pas une 502 : Open Library a
     * repondu sans erreur, c'est la reference qui n'existe pas.
     */
    @ExceptionHandler(OuvrageIntrouvableException.class)
    public ResponseEntity<Map<String, String>> handleIntrouvable(OuvrageIntrouvableException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    /** Autres erreurs portant un code HTTP explicite. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        HttpStatus code = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String raison = e.getReason() == null ? e.getMessage() : e.getReason();
        return ResponseEntity.status(code).body(Map.of("error", raison));
    }
}
