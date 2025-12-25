package com.bootsandcats.oauth2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Provides a no-op transaction manager when running without a database.
 *
 * <p>Several services use {@code @Transactional} for JPA-backed implementations. In the {@code
 * prod-no-db} profile we intentionally disable DataSource/JPA/Flyway. Spring still needs a {@link
 * PlatformTransactionManager} for transactional advice; this resourceless manager satisfies that
 * requirement without requiring a database.
 */
@Configuration
@Profile("prod-no-db")
public class NoDatabaseTransactionConfig {

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager transactionManager() {
        return new NoOpTransactionManager();
    }
}
