package com.bootsandcats.oauth2.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.util.ReflectionTestUtils;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.service.SecurityAuditService;

class DataInitializerTest {

    @TempDir Path tempDir;

    @Test
    void resolveSecureSubdomainClientSecret_prefersFileValue_whenPresent() throws Exception {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);

        DataInitializer initializer =
                new DataInitializer(repository, passwordEncoder, securityAuditService);

        Path secretFile = tempDir.resolve("secure-subdomain-client-secret");
        Files.writeString(secretFile, "from-file-secret\n");

        ReflectionTestUtils.setField(initializer, "secureSubdomainClientSecret", "from-env-secret");
        ReflectionTestUtils.setField(
                initializer, "secureSubdomainClientSecretFile", secretFile.toString());

        assertThat(initializer.resolveSecureSubdomainClientSecret()).isEqualTo("from-file-secret");
    }

    @Test
    void resolveSecureSubdomainClientSecret_fallsBackToConfiguredValue_whenFileMissing() {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);

        DataInitializer initializer =
                new DataInitializer(repository, passwordEncoder, securityAuditService);

        ReflectionTestUtils.setField(initializer, "secureSubdomainClientSecret", "from-env-secret");
        ReflectionTestUtils.setField(
                initializer,
                "secureSubdomainClientSecretFile",
                tempDir.resolve("does-not-exist").toString());

        assertThat(initializer.resolveSecureSubdomainClientSecret()).isEqualTo("from-env-secret");
    }

    @Test
    void reconcileSecureSubdomainClientSecret_updatesStoredSecret_whenMismatchDetected()
            throws Exception {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        SecurityAuditService securityAuditService = mock(SecurityAuditService.class);

        DataInitializer initializer =
                new DataInitializer(repository, passwordEncoder, securityAuditService);

        Path secretFile = tempDir.resolve("secure-subdomain-client-secret");
        Files.writeString(secretFile, "new-rotated-secret\n");

        ReflectionTestUtils.setField(initializer, "syncClientSecrets", true);
        ReflectionTestUtils.setField(
                initializer, "secureSubdomainClientSecret", "stale-env-secret");
        ReflectionTestUtils.setField(
                initializer, "secureSubdomainClientSecretFile", secretFile.toString());

        RegisteredClient existing =
                RegisteredClient.withId("id")
                        .clientId("secure-subdomain-client")
                        .clientIdIssuedAt(Instant.now())
                        .clientSecret(passwordEncoder.encode("old-secret"))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .build();

        when(repository.findByClientId("secure-subdomain-client")).thenReturn(existing);

        initializer.reconcileSecureSubdomainClientSecret();

        verify(repository).save(any(RegisteredClient.class));
        verify(securityAuditService, atLeastOnce())
                .recordGenericEvent(
                        eq(AuditEventType.CLIENT_UPDATED),
                        eq(AuditEventResult.SUCCESS),
                        eq("secure-subdomain-client"),
                        isNull(),
                        anyMap());
    }
}
