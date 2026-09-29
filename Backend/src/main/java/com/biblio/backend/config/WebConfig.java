package com.biblio.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Client HTTP utilise pour interroger l'API Open Library.
     * L'adresse de base est fixee par OpenLibraryService.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Pas d'authentification sur cette page d'accueil : on autorise simplement
     * le front Angular (http://localhost:4200) a appeler l'API.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET")
                .allowCredentials(false);
    }
}
