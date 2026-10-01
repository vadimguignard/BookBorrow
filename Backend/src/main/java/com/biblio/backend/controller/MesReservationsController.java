package com.biblio.backend.controller;

import com.biblio.backend.dto.MesReservationDto;
import com.biblio.backend.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reservations de l'utilisateur connecte (toutes les routes exigent un jeton).
 *
 * GET    /api/reservations/mes   ses reservations, la plus recente d'abord
 * DELETE /api/reservations/{id}  annule l'une de ses reservations (204, 404 si ce n'est pas la sienne)
 */
@RestController
@RequestMapping("/api/reservations")
public class MesReservationsController {

    private final ReservationService reservationService;

    public MesReservationsController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/mes")
    public List<MesReservationDto> mesReservations(@AuthenticationPrincipal Long utilisateurId) {
        return reservationService.mesReservations(utilisateurId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void annuler(@PathVariable Long id, @AuthenticationPrincipal Long utilisateurId) {
        reservationService.annuler(utilisateurId, id);
    }
}
