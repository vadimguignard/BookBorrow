package com.biblio.backend.controller;

import com.biblio.backend.dto.PeriodeReserveeDto;
import com.biblio.backend.dto.ReservationDemandeDto;
import com.biblio.backend.dto.ReservationDto;
import com.biblio.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reservation d'un livre.
 *
 * Comme pour la fiche, la reference Open Library (« /works/OL82563W ») contient des « / » :
 * elle voyage en parametre de requete (GET) ou dans le corps (POST), jamais dans le chemin.
 */
@RestController
@RequestMapping("/api/livres/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** GET /api/livres/reservations?reference=/works/OL82563W : periodes deja prises (pour griser le calendrier). */
    @GetMapping
    public List<PeriodeReserveeDto> periodes(@RequestParam(name = "reference") String reference) {
        return reservationService.periodesReservees(reference);
    }

    /**
     * POST /api/livres/reservations
     * Corps : { "reference", "prenom", "nom", "dateDebut", "dateFin" } (dates AAAA-MM-JJ).
     * 201 si enregistree, 409 si la periode chevauche une autre reservation, 400 si incoherente.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto reserver(@Valid @RequestBody ReservationDemandeDto demande) {
        return reservationService.reserver(demande);
    }
}
