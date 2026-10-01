package com.biblio.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un compte utilisateur de la bibliotheque.
 *
 * Le mot de passe n'est jamais stocke en clair : seul son hachage BCrypt est
 * conserve dans {@link #motDePasse}. Le username est l'identifiant de connexion
 * (unique, sans distinction majuscules / minuscules a l'inscription).
 */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    /** Hachage BCrypt du mot de passe. */
    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;

    @Column(name = "date_inscription", nullable = false)
    private LocalDate dateInscription;

    protected Utilisateur() {
        // requis par JPA
    }

    public Utilisateur(String username, String motDePasseHache) {
        this.username = username;
        this.motDePasse = motDePasseHache;
        this.dateInscription = LocalDate.now();
    }
}
