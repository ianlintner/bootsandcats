package com.bootsandcats.oauth2.config;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * A minimal no-op {@link PlatformTransactionManager}.
 *
 * <p>This is used when running without a database (e.g. {@code prod-no-db}) so that
 * {@code @Transactional} advice can execute without requiring a JDBC/JPA transaction manager.
 */
public final class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // no-op
    }

    @Override
    public void rollback(TransactionStatus status) {
        // no-op
    }
}
