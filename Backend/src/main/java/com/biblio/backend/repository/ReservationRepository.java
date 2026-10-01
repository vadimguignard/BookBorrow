package com.biblio.backend.repository;

import com.biblio.backend.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Vrai si une reservation de ce livre chevauche la periode [debut, fin] (bornes incluses). */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.livre.id = :livreId
              AND r.dateDebut <= :fin
              AND r.dateFin >= :debut
            """)
    boolean existeChevauchement(@Param("livreId") Long livreId,
                                @Param("debut") LocalDate debut,
                                @Param("fin") LocalDate fin);

    /** Reservations pas encore terminees d'un livre, de la plus proche a la plus lointaine. */
    List<Reservation> findByLivreIdAndDateFinGreaterThanEqualOrderByDateDebutAsc(Long livreId, LocalDate depuis);
}
