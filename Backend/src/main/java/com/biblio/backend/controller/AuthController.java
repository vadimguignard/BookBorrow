package com.biblio.backend.controller;

import com.biblio.backend.dto.AuthReponseDto;
import com.biblio.backend.dto.ConnexionDto;
import com.biblio.backend.dto.InscriptionDto;
import com.biblio.backend.dto.UtilisateurDto;
import com.biblio.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification et profil.
 *
 * POST /api/auth/inscription   public : cree le compte et renvoie directement un jeton (201)
 * POST /api/auth/connexion     public : 200 + jeton, 401 si identifiants incorrects
 * GET  /api/auth/moi           connecte : informations du profil
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthReponseDto inscription(@Valid @RequestBody InscriptionDto demande) {
        return authService.inscrire(demande);
    }

    @PostMapping("/connexion")
    public AuthReponseDto connexion(@Valid @RequestBody ConnexionDto demande) {
        return authService.connecter(demande);
    }

    @GetMapping("/moi")
    public UtilisateurDto moi(@AuthenticationPrincipal Long utilisateurId) {
        return authService.profil(utilisateurId);
    }

}
