package com.biblio.backend.domain;

/**
 * Statut d'un livre dans NOTRE application.
 *
 * Cette information n'est jamais fournie par Open Library : elle appartient
 * exclusivement a la base de donnees de la bibliotheque.
 */
public enum Statut {

    /** Le livre est disponible, il peut etre emprunte. */
    LIBRE,

    /** Le livre est reserve, il redevient disponible a dateDisponibilite. */
    RESERVE
}
