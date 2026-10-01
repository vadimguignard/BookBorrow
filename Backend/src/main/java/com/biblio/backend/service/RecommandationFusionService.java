package com.biblio.backend.service;

import com.biblio.backend.domain.Livre;
import com.biblio.backend.domain.Statut;
import com.biblio.backend.dto.LivreCatalogue;
import com.biblio.backend.dto.LivreDto;
import com.biblio.backend.repository.LivreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application unique de la frontiere entre les deux sources de donnees.
 *
 * Regle unique et non negociable : le statut (LIBRE / RESERVE) et la date de
 * disponibilite proviennent TOUJOURS de notre base, jamais d'Open Library.
 * Un livre absent de la base est LIBRE : il n'a donc pas de date de
 * disponibilite, ce qui est coherent.
 *
 * Cette regle est appliquee ici, et nulle part ailleurs, pour qu'aucun autre
 * ecran ne puisse la contourner par megarde.
 */
@Service
public class RecommandationFusionService {

    private final LivreRepository livreRepository;

    public RecommandationFusionService(LivreRepository livreRepository) {
        this.livreRepository = livreRepository;
    }

    /** DTO simple (accueil, recommandations) : titre, auteur, annee, couverture, statut. */
    @Transactional(readOnly = true)
    public LivreDto versDto(LivreCatalogue item) {
        return versDto(item, livreLocal(item.reference()));
    }

    /** DTO simple pour une liste de livres du catalogue. */
    @Transactional(readOnly = true)
    public List<LivreDto> versDto(List<LivreCatalogue> items) {
        Map<String, Livre> parReference = livreRepository
                .findByReferenceIn(items.stream().map(LivreCatalogue::reference).toList())
                .stream()
                .collect(Collectors.toMap(Livre::getReference, Function.identity(), (a, b) -> a, HashMap::new));

        return items.stream()
                .map(item -> versDto(item, parReference.get(item.reference())))
                .toList();
    }

    /** Livre de notre base pour une reference, ou null s'il n'y est pas encore. */
    @Transactional(readOnly = true)
    public Livre livreLocal(String reference) {
        return reference == null ? null : livreRepository.findByReference(reference).orElse(null);
    }

    private LivreDto versDto(LivreCatalogue item, Livre local) {
        Statut statut = local != null ? local.getStatut() : Statut.LIBRE;
        LocalDate dateDisponibilite = local != null ? local.getDateDisponibilite() : null;

        // Un livre LIBRE n'a pas de date de disponibilite : elle ne concerne que les reservations.
        if (statut != Statut.RESERVE) {
            dateDisponibilite = null;
        }
        return new LivreDto(
                item.reference(),
                item.titre(),
                item.auteur(),
                item.anneePublication(),
                item.coverUrl(),
                statut,
                dateDisponibilite,
                statut == Statut.LIBRE);
    }
}
