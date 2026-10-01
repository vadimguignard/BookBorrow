package com.biblio.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Une reservation d'un livre de la bibliotheque sur une periode donnee.
 *
 * Les deux bornes sont INCLUSES : un livre reserve du 5 au 10 est indisponible
 * le 5 et le 10, et redevient disponible le 11.
 *
 * Le statut et la date de disponibilite du {@link Livre} sont deduits de ces
 * reservations (voir ReservationService).
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "livre_id", nullable = false)
    private Livre livre;

    /**
     * Auteur de la reservation. Nullable : les reservations creees avant l'arrivee
     * des comptes n'ont pas d'utilisateur.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    protected Reservation() {
        // requis par JPA
    }

    /** Reservation faite par un utilisateur connecte. */
    public Reservation(Livre livre, Utilisateur utilisateur, LocalDate dateDebut, LocalDate dateFin) {
        this(livre, dateDebut, dateFin);
        this.utilisateur = utilisateur;
    }

    public Reservation(Livre livre, LocalDate dateDebut, LocalDate dateFin) {
        this.livre = livre;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }
}
