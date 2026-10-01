package com.biblio.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les taches planifiees (mise a jour quotidienne du statut des livres). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
