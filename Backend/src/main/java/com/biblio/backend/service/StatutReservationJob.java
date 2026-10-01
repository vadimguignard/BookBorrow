package com.biblio.backend.service;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Garde le statut LIBRE / RESERVE des livres a jour au fil des jours. */
@Component
public class StatutReservationJob {

    private final ReservationService reservationService;

    public StatutReservationJob(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Au demarrage : rattrape les jours ecoules depuis le dernier arret. */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        try {
            reservationService.synchroniserStatuts();
        } catch (RuntimeException e) {
            // Ne jamais empecher l'application de demarrer pour une mise a jour de statut.
            LoggerFactory.getLogger(StatutReservationJob.class).warn("Synchronisation des statuts impossible : {}", e.getMessage());
        }
    }

    /** Chaque nuit, juste apres minuit. */
    @Scheduled(cron = "0 5 0 * * *")
    public void chaqueNuit() {
        reservationService.synchroniserStatuts();
    }
}
