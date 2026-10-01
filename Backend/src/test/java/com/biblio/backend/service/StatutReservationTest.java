package com.biblio.backend.service;

import com.biblio.backend.domain.Reservation;
import com.biblio.backend.domain.Statut;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatutReservationTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 1);

    private static Reservation resa(int debutDansJours, int finDansJours) {
        return new Reservation(null, "Ada", "Lovelace",
                AUJOURDHUI.plusDays(debutDansJours), AUJOURDHUI.plusDays(finDansJours));
    }

    @Test
    void sansReservationLeLivreEstLibre() {
        var r = ReservationService.calculerStatut(List.of(), AUJOURDHUI);
        assertEquals(Statut.LIBRE, r.statut());
        assertNull(r.dateDisponibilite());
    }

    @Test
    void reservationFutureLaisseLeLivreLibreAujourdhui() {
        var r = ReservationService.calculerStatut(List.of(resa(3, 6)), AUJOURDHUI);
        assertEquals(Statut.LIBRE, r.statut());
        assertNull(r.dateDisponibilite());
    }

    @Test
    void reservationEnCoursReserveLeLivreJusquAuLendemainDeLaFin() {
        var r = ReservationService.calculerStatut(List.of(resa(-2, 4)), AUJOURDHUI);
        assertEquals(Statut.RESERVE, r.statut());
        assertEquals(AUJOURDHUI.plusDays(5), r.dateDisponibilite());
    }

    @Test
    void reservationQuiFinitAujourdhuiOccupeEncoreLeLivreAujourdhui() {
        var r = ReservationService.calculerStatut(List.of(resa(-3, 0)), AUJOURDHUI);
        assertEquals(Statut.RESERVE, r.statut());
        assertEquals(AUJOURDHUI.plusDays(1), r.dateDisponibilite());
    }

    @Test
    void reservationsQuiSEnchainentComptentCommeUne() {
        var r = ReservationService.calculerStatut(List.of(resa(-1, 3), resa(4, 7), resa(10, 12)), AUJOURDHUI);
        assertEquals(Statut.RESERVE, r.statut());
        assertEquals(AUJOURDHUI.plusDays(8), r.dateDisponibilite()); // 10-12 est separee par un trou : ignoree
    }
}
