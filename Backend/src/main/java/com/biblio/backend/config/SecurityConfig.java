package com.biblio.backend.config;

import com.biblio.backend.security.JwtAuthFilter;
import com.biblio.backend.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Securite de l'API.
 *
 * - Le catalogue, les fiches, les recommandations et le calendrier d'un livre restent publics.
 * - Inscription et connexion sont publiques.
 * - Reserver, annuler, consulter son profil et ses reservations exigent d'etre connecte.
 *
 * L'API est sans session : chaque requete protegee porte son jeton dans l'en-tete
 * Authorization. Sans jeton valide, la reponse est un 401 (et non une page de login).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // pas de cookie de session : pas de CSRF
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/inscription", "/api/auth/connexion").permitAll()
                        .requestMatchers("/api/auth/**", "/api/reservations/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/livres/reservations").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
