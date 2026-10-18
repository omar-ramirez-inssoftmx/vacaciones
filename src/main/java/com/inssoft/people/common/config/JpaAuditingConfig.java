package com.inssoft.people.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita el llenado automático de {@code createdAt} / {@code updatedAt}
 * en las entidades nuevas que extienden {@code BaseEntity}.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
