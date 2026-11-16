package com.ecourtier.testsupport;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Nettoie l'ensemble des tables applicatives dans l'ordre strict des dépendances
 * (tables enfants avant tables parents) afin de garantir l'isolation entre tests
 * d'intégration partageant la base H2 en mémoire quel que soit l'ordre d'exécution Surefire.
 */
@Component
public class TestDatabaseCleaner {

    private static final List<String> TABLES_DELETE_ORDER = List.of(
            "messages_ia",
            "conversations_ia",
            "messages_chat",
            "conversations_chat",
            "notifications_sms",
            "paiements_wave",
            "signalements",
            "avis",
            "favoris",
            "documents_verification",
            "photos_annonces",
            "equipements_annonces",
            "proximites_annonces",
            "transactions",
            "rendez_vous",
            "refresh_tokens",
            "annonces",
            "utilisateurs",
            "scheduler_locks"
    );

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    private final ObjectProvider<EntityManager> entityManagerProvider;

    public TestDatabaseCleaner(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            ObjectProvider<EntityManager> entityManagerProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.transactionManagerProvider = transactionManagerProvider;
        this.entityManagerProvider = entityManagerProvider;
    }

    public void cleanAll() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        PlatformTransactionManager txManager = transactionManagerProvider.getIfAvailable();
        if (jdbcTemplate == null || txManager == null) {
            return;
        }
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.executeWithoutResult(status -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                EntityManager em = entityManagerProvider.getIfAvailable();
                if (em != null) {
                    em.clear();
                }
            }
            for (String table : TABLES_DELETE_ORDER) {
                jdbcTemplate.execute("DELETE FROM " + table);
            }
        });
    }
}
