package com.biblio.backend.controller;

import com.biblio.backend.dto.LivreDetailDto;
import com.biblio.backend.dto.LivreDto;
import com.biblio.backend.service.LivreDetailService;
import com.biblio.backend.service.RecommandationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/livres")
public class LivreDetailController {

    private final LivreDetailService livreDetailService;
    private final RecommandationService recommandationService;

    public LivreDetailController(
            LivreDetailService livreDetailService,
            RecommandationService recommandationService) {
        this.livreDetailService = livreDetailService;
        this.recommandationService = recommandationService;
    }

    /**
     * GET /api/livres/detail?reference=/works/OL82563W
     *
     * Fiche complete d'un livre.
     *
     * La reference est un parametre de requete et non un segment de chemin :
     * elle contient des « / » (ex. /works/OL82563W) et Tomcat refuse un %2F
     * dans un segment, en renvoyant un 400 avant meme d'atteindre le
     * controleur. Le client encode donc la reference, ou l'envoie telle quelle.
     */
    @GetMapping("/detail")
    public LivreDetailDto detail(@RequestParam(name = "reference") String reference) {
        return livreDetailService.detail(reference);
    }

    /**
     * GET /api/livres/recommandations?reference=/works/OL82563W&limite=10
     *
     * Livres proches du livre consulte, choisis par GENRE (sujets Open
     * Library). Le livre lui-meme n'est jamais recommande.
     */
    @GetMapping("/recommandations")
    public List<LivreDto> recommandations(
            @RequestParam(name = "reference") String reference,
            @RequestParam(name = "limite", required = false, defaultValue = "10") Integer limite) {
        return recommandationService.pourLivre(reference, limite);
    }
}
