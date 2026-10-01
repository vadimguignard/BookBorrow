package com.biblio.backend.repository;

import com.biblio.backend.domain.Livre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LivreRepository extends JpaRepository<Livre, Long> {

    Optional<Livre> findByReference(String reference);

    /**
     * Recuperation des livres de notre bibliotheque selon des criteres optionnels.
     *
     * Chaque critere est combine avec un AND : seuls les livres qui respectent
     * TOUS les criteres actifs sont renvoyes. Les criteres absents (null ou vide)
     * ne filtrent pas. Le titre ET l'auteur sont compares en minuscules pour que
     * la recherche soit insensible a la casse.
     */
    @Query("""
            SELECT l FROM Livre l
            WHERE (:q IS NULL OR LOWER(l.titre) LIKE LOWER(CONCAT('%', :q, '%'))
                               OR LOWER(l.auteur) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:titre IS NULL OR LOWER(l.titre) LIKE LOWER(CONCAT('%', :titre, '%')))
              AND (:auteur IS NULL OR LOWER(l.auteur) LIKE LOWER(CONCAT('%', :auteur, '%')))
              AND (:annee IS NULL OR l.anneePublication = :annee)
            ORDER BY l.titre ASC
            """)
    List<Livre> search(@Param("q") String q,
                       @Param("titre") String titre,
                       @Param("auteur") String auteur,
                       @Param("annee") Integer annee);

    /** Index de la page d'accueil : un livre Open Library peut ne pas etre dans notre base. */
    List<Livre> findByReferenceIn(Collection<String> references);
}
