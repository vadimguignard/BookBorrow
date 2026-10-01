package com.biblio.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un livre de NOTRE bibliotheque.
 *
 * Les informations generales (titre, auteur, annee, couverture) proviennent
 * d'Open Library et sont refreshes par le catalogue.
 *
 * En revanche {@link #statut} et {@link #dateDisponibilite} n'appartiennent
 * qu'a notre application : ils sont stockes ici et ne sont jamais fournis
 * par Open Library.
 */
@Entity
@Table(name = "livre")
@Getter
@Setter
public class Livre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cle du livre chez Open Library (ex : /works/OL82563W).
     * Sert de clef de jointure entre notre base et le catalogue Open Library.
     */
    @Column(name = "reference", nullable = false, unique = true, length = 64)
    private String reference;

    @Column(nullable = false, length = 512)
    private String titre;

    @Column(nullable = false, length = 255)
    private String auteur;

    @Column(name = "annee_publication")
    private Integer anneePublication;

    @Column(name = "cover_id")
    private Long coverId;

    /** Statut metier : LIBRE ou RESERVE. Provenance exclusive : notre base. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Statut statut = Statut.LIBRE;

    /**
     * Date a laquelle le livre sera de nouveau disponible.
     * Renseignee uniquement quand le statut est RESERVE, sinon null.
     */
    @Column(name = "date_disponibilite")
    private LocalDate dateDisponibilite;
}
