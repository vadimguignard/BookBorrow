package com.biblio.backend.dto;

import java.util.List;

/**
 * Une page de 25 livres pour la page d'accueil.
 *
 * totalElements / totalPages permettent a l'interface d'afficher la pagination
 * sans avoir a recharger le catalogue complet.
 */
public record PageLivresDto(
        List<LivreDto> livres,
        int page,
        int taillePage,
        long totalElements,
        int totalPages
) {
}
