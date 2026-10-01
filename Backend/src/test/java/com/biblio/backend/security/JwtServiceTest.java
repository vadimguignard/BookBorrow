package com.biblio.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "un-secret-de-test-assez-long-0123456789";
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private static JwtService service(String secret, Instant maintenant) {
        return new JwtService(secret, Duration.ofHours(24), Clock.fixed(maintenant, ZoneOffset.UTC));
    }

    @Test
    void unJetonValideRetrouveLUtilisateur() {
        JwtService jwt = service(SECRET, T0);
        assertEquals(42L, jwt.verifier(jwt.generer(42L)).orElseThrow());
    }

    @Test
    void unJetonExpireEstRefuse() {
        String jeton = service(SECRET, T0).generer(42L);
        assertTrue(service(SECRET, T0.plus(Duration.ofHours(25))).verifier(jeton).isEmpty());
    }

    @Test
    void unJetonSigneAvecUnAutreSecretEstRefuse() {
        String jeton = service("un-autre-secret-de-test-assez-long-987654", T0).generer(42L);
        assertTrue(service(SECRET, T0).verifier(jeton).isEmpty());
    }

    @Test
    void unJetonFalsifieEstRefuse() {
        JwtService jwt = service(SECRET, T0);
        String[] morceaux = jwt.generer(42L).split("\\.");
        // On remplace la charge par celle de l'utilisateur 1 en gardant l'ancienne signature.
        String charge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1:" + T0.plus(Duration.ofHours(24)).getEpochSecond()).getBytes());
        assertTrue(jwt.verifier(charge + "." + morceaux[1]).isEmpty());
    }

    @Test
    void desJetonsMalFormesSontRefuses() {
        JwtService jwt = service(SECRET, T0);
        for (String mauvais : new String[]{null, "", "abc", ".", "a.", ".b", "!!!.???"}) {
            assertTrue(jwt.verifier(mauvais).isEmpty(), "devrait refuser : " + mauvais);
        }
    }

    @Test
    void unSecretTropCourtEstRefuseAuDemarrage() {
        assertThrows(IllegalStateException.class, () -> service("court", T0));
    }
}
