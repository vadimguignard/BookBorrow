package com.biblio.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lit l'en-tete « Authorization: Bearer <jeton> » et, si le jeton est valide,
 * authentifie la requete. Le principal est l'identifiant (Long) de l'utilisateur.
 *
 * Volontairement PAS un @Component : il est instancie dans SecurityConfig, sinon
 * Spring Boot l'enregistrerait aussi comme filtre servlet et il s'executerait deux fois.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String entete = request.getHeader("Authorization");
        if (entete != null && entete.startsWith(PREFIXE)) {
            jwtService.verifier(entete.substring(PREFIXE.length()).trim()).ifPresent(id -> {
                var authentification = new UsernamePasswordAuthenticationToken(
                        id, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentification);
            });
        }
        chain.doFilter(request, response);
    }
}
