package com.bootsandcats.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Verifies the "prod-no-db" profile truly disables all DB/JPA/Flyway wiring.
 *
 * <p>This is the production posture when durable configuration comes from Kubernetes CRDs and
 * runtime state uses Redis (or none during tests).
 */
@SpringBootTest
@ActiveProfiles({"prod", "prod-no-flyway", "prod-no-db"})
@TestPropertySource(
        properties = {
            "spring.session.store-type=none",
            "spring.main.allow-bean-definition-overriding=true",
            "auth.events.enabled=false",
            "oauth2.issuer-url=http://localhost:9000",
            "oauth2.demo-client-secret=test-demo-secret",
            "oauth2.m2m-client-secret=test-m2m-secret",
            "oauth2.demo-user-password=test-user-password",
            "oauth2.admin-user-password=test-admin-password",
            "azure.keyvault.enabled=false"
        })
class ProdNoDbProfileIntegrationTest {

    private static final String STATIC_JWK_JSON =
            "{\"keys\":[{\"kty\":\"EC\",\"d\":\"mwhKr9BIDjuB-OajeULLA4RORdJLUL8816YenVZwlMs\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"586e7c9b-a4dc-4ea9-9cf7-197c3fae5d7f\",\"x\":\"0yuOTwftybMpxjSc1liSpftWHi5-YyyqvdlYclgF4zw\",\"y\":\"qboYXttcfjXXSYFlUEMkBOVmsMMDATyRv-UN4AR8Fl0\",\"alg\":\"ES256\"}]}";

    @DynamicPropertySource
    static void configureJwk(DynamicPropertyRegistry registry) {
        registry.add("azure.keyvault.static-jwk", () -> STATIC_JWK_JSON);
    }

    @TestConfiguration
    static class KubernetesClientTestConfig {

        @Bean
        KubernetesClient kubernetesClient() {
            KubernetesClient client =
                    Mockito.mock(KubernetesClient.class, Mockito.RETURNS_DEEP_STUBS);
            Mockito.when(client.getNamespace()).thenReturn("default");
            return client;
        }
    }

    @Test
    void contextLoadsWithoutDatabaseBeans(ApplicationContext applicationContext) {
        assertThat(applicationContext).isNotNull();

        // No JDBC DataSource should be created.
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();

        // No Flyway bean should be created.
        try {
            applicationContext.getBean(Flyway.class);
            throw new AssertionError("Flyway bean should not exist when prod-no-db is active");
        } catch (NoSuchBeanDefinitionException ignored) {
            // expected
        }
    }
}
