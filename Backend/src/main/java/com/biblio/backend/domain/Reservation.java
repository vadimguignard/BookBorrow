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

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    protected Reservation() {
        // requis par JPA
    }

    public Reservation(Livre livre, String prenom, String nom, LocalDate dateDebut, LocalDate dateFin) {
        this.livre = livre;
        this.prenom = prenom;
        this.nom = nom;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }
}
