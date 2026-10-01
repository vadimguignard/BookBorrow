package com.biblio.backend.service;

import com.biblio.backend.dto.LivreCatalogue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mise en cache du catalogue Open Library.
 *
 * L'API Open Library met 5 a 10 secondes pour repondre : sans cache, changer de
 * page ou ajouter un filtre ferait attendre l'utilisateur a chaque clic.
 * On conserve donc le catalogue quelques minutes en memoire.
 */
@Service
public class CatalogueCache {

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Duration dureeDeVie;

    public CatalogueCache(@Value("${catalogue.cache-duree-secondes:300}") long dureeSecondes) {
        this.dureeDeVie = Duration.ofSeconds(dureeSecondes);
    }

    /** Retourne le catalogue mis en cache pour cette requete, ou null s'il est absent ou perime. */
    public List<LivreCatalogue> get(String cle) {
        Entry entry = cache.get(cle);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expireLe())) {
            cache.remove(cle);
            return null;
        }
        return entry.livres();
    }

    public void put(String cle, List<LivreCatalogue> livres) {
        cache.put(cle, new Entry(livres, Instant.now().plus(dureeDeVie)));
    }

    private record Entry(List<LivreCatalogue> livres, Instant expireLe) {
    }
}
