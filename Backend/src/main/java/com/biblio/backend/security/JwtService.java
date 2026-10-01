package com.biblio.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Jeton d'authentification signe en HMAC-SHA256.
 *
 * Format : base64url("idUtilisateur:expirationEnSecondes") + "." + base64url(signature).
 * Le serveur ne garde aucun etat : un jeton valide (signature correcte, non expire)
 * suffit a identifier l'utilisateur. Aucune bibliotheque externe n'est necessaire.
 */
@Service
public class JwtService {

    private static final String ALGORITHME = "HmacSHA256";
    private static final Base64.Encoder ENCODEUR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODEUR = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration validite;
    private final Clock horloge;

    // Deux constructeurs : @Autowired indique a Spring lequel utiliser (l'autre sert aux tests).
    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.duree-heures:24}") long dureeHeures) {
        this(secret, Duration.ofHours(dureeHeures), Clock.systemUTC());
    }

    JwtService(String secret, Duration validite, Clock horloge) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret doit faire au moins 32 caracteres (variable d'environnement JWT_SECRET).");
        }
        this.secret = octets;
        this.validite = validite;
        this.horloge = horloge;
    }

    /** Genere un jeton valable {@code validite} pour cet utilisateur. */
    public String generer(Long utilisateurId) {
        long expiration = horloge.instant().plus(validite).getEpochSecond();
        String charge = ENCODEUR.encodeToString(
                (utilisateurId + ":" + expiration).getBytes(StandardCharsets.UTF_8));
        return charge + "." + ENCODEUR.encodeToString(signer(charge));
    }

    /** Identifiant de l'utilisateur si le jeton est authentique et non expire, sinon vide. */
    public Optional<Long> verifier(String jeton) {
        if (jeton == null) {
            return Optional.empty();
        }
        int point = jeton.indexOf('.');
        if (point <= 0 || point == jeton.length() - 1) {
            return Optional.empty();
        }
        try {
            String charge = jeton.substring(0, point);
            byte[] signature = DECODEUR.decode(jeton.substring(point + 1));
            // Comparaison en temps constant : ne revele rien sur la signature attendue.
            if (!MessageDigest.isEqual(signature, signer(charge))) {
                return Optional.empty();
            }
            String[] morceaux = new String(DECODEUR.decode(charge), StandardCharsets.UTF_8).split(":");
            if (morceaux.length != 2) {
                return Optional.empty();
            }
            long expiration = Long.parseLong(morceaux[1]);
            if (horloge.instant().getEpochSecond() >= expiration) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(morceaux[0]));
        } catch (IllegalArgumentException e) {
            // Base64 invalide ou nombre illisible : jeton falsifie.
            return Optional.empty();
        }
    }

    private byte[] signer(String charge) {
        try {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(new SecretKeySpec(secret, ALGORITHME));
            return mac.doFinal(charge.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
