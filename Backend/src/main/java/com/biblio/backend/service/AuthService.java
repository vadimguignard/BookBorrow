package com.biblio.backend.service;

import com.biblio.backend.domain.Utilisateur;
import com.biblio.backend.dto.AuthReponseDto;
import com.biblio.backend.dto.ConnexionDto;
import com.biblio.backend.dto.InscriptionDto;
import com.biblio.backend.dto.UtilisateurDto;
import com.biblio.backend.repository.ReservationRepository;
import com.biblio.backend.repository.UtilisateurRepository;
import com.biblio.backend.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/** Inscription, connexion et profil des utilisateurs. */
@Service
public class AuthService {

    /** Message unique pour « username inconnu » et « mauvais mot de passe » : on ne revele pas lequel. */
    static final String IDENTIFIANTS_INVALIDES = "Nom d'utilisateur ou mot de passe incorrect.";

    private static final Pattern FORMAT_USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    private static final int MDP_MIN = 8;
    private static final int MDP_MAX = 64;

    private final UtilisateurRepository utilisateurRepository;
    private final ReservationRepository reservationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Hachage factice : permet de depenser le meme temps quand le username est inconnu. */
    private final String hachageFactice;

    public AuthService(UtilisateurRepository utilisateurRepository,
                       ReservationRepository reservationRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.utilisateurRepository = utilisateurRepository;
        this.reservationRepository = reservationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hachageFactice = passwordEncoder.encode("mot-de-passe-factice");
    }

    @Transactional
    public AuthReponseDto inscrire(InscriptionDto demande) {
        String username = demande.username().trim();
        verifierUsername(username);
        verifierMotDePasse(demande.motDePasse());
        if (!demande.motDePasse().equals(demande.confirmationMotDePasse())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les mots de passe ne correspondent pas.");
        }
        if (utilisateurRepository.existsByUsernameIgnoreCase(username)) {
            throw usernameDejaUtilise();
        }

        // Le mot de passe n'est jamais stocke en clair : seul le hachage BCrypt est enregistre.
        Utilisateur utilisateur = new Utilisateur(username, passwordEncoder.encode(demande.motDePasse()));
        try {
            utilisateur = utilisateurRepository.saveAndFlush(utilisateur);
        } catch (DataIntegrityViolationException e) {
            // Deux inscriptions simultanees avec le meme username : la contrainte UNIQUE tranche.
            throw usernameDejaUtilise();
        }
        return reponse(utilisateur);
    }

    @Transactional(readOnly = true)
    public AuthReponseDto connecter(ConnexionDto demande) {
        Utilisateur utilisateur = utilisateurRepository
                .findByUsernameIgnoreCase(demande.username().trim()).orElse(null);
        String hachage = utilisateur == null ? hachageFactice : utilisateur.getMotDePasse();
        boolean correct = passwordEncoder.matches(demande.motDePasse(), hachage);
        if (utilisateur == null || !correct) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, IDENTIFIANTS_INVALIDES);
        }
        return reponse(utilisateur);
    }

    @Transactional(readOnly = true)
    public UtilisateurDto profil(Long utilisateurId) {
        return versDto(charger(utilisateurId));
    }

    /** Utilisateur du jeton ; 401 s'il a ete supprime depuis l'emission du jeton. */
    public Utilisateur charger(Long utilisateurId) {
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée."));
    }

    // ------------------------------------------------------------------ validation

    static void verifierUsername(String username) {
        if (!FORMAT_USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nom d'utilisateur doit contenir entre 3 et 30 caractères "
                            + "(lettres, chiffres, point, tiret ou underscore).");
        }
    }

    /** Criteres de securite : 8 caracteres minimum, au moins une majuscule et un chiffre. */
    static void verifierMotDePasse(String motDePasse) {
        if (motDePasse.length() < MDP_MIN || motDePasse.length() > MDP_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir entre " + MDP_MIN + " et " + MDP_MAX + " caractères.");
        }
        if (motDePasse.chars().noneMatch(Character::isUpperCase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au moins une majuscule.");
        }
        if (motDePasse.chars().noneMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au moins un chiffre.");
        }
    }

    // ------------------------------------------------------------------ interne

    private AuthReponseDto reponse(Utilisateur utilisateur) {
        return new AuthReponseDto(jwtService.generer(utilisateur.getId()), versDto(utilisateur));
    }

    private UtilisateurDto versDto(Utilisateur u) {
        return new UtilisateurDto(u.getId(), u.getUsername(), u.getDateInscription(),
                reservationRepository.countByUtilisateurId(u.getId()));
    }

    private static ResponseStatusException usernameDejaUtilise() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ce nom d'utilisateur est déjà utilisé.");
    }
}
