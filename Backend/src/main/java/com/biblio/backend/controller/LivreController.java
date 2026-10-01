package com.biblio.backend.controller;

import com.biblio.backend.dto.PageLivresDto;
import com.biblio.backend.service.LivreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/livres")
public class LivreController {

    private final LivreService livreService;

    public LivreController(LivreService livreService) {
        this.livreService = livreService;
    }

    /**
     * GET /api/livres?q=&auteur=&titre=&annee=&page=
     *
     * Recherche libre sur le titre OU l'auteur (insensible a la casse) combinee
     * en AND avec les filtres auteur, titre et annee. Les resultats sont pagines
     * par 25.
     */
    @GetMapping
    public PageLivresDto pageDaccueil(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "auteur", required = false) String auteur,
            @RequestParam(name = "titre", required = false) String titre,
            @RequestParam(name = "annee", required = false) Integer annee,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page) {
        return livreService.pageDaccueil(q, auteur, titre, annee, page);
    }
}
