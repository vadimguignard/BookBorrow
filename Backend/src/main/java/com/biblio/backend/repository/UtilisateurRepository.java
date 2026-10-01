package com.biblio.backend.repository;

import com.biblio.backend.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
