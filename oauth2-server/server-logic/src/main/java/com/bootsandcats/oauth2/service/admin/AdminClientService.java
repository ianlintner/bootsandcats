package com.bootsandcats.oauth2.service.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.dto.admin.AdminClientSummary;
import com.bootsandcats.oauth2.dto.admin.AdminClientUpsertRequest;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.ClientMetadataEntity;
import com.bootsandcats.oauth2.model.ClientScopeEntity;
import com.bootsandcats.oauth2.model.ClientScopeId;
import com.bootsandcats.oauth2.model.ScopeEntity;
import com.bootsandcats.oauth2.repository.ClientMetadataRepository;
import com.bootsandcats.oauth2.repository.ClientScopeRepository;
import com.bootsandcats.oauth2.repository.RegisteredClientJpaRepository;
import com.bootsandcats.oauth2.repository.ScopeRepository;
import com.bootsandcats.oauth2.service.JpaRegisteredClientRepository;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminClientService {

    private final JpaRegisteredClientRepository jpaRegisteredClientRepository;
    private final RegisteredClientJpaRepository registeredClientJpaRepository;
    private final ClientMetadataRepository clientMetadataRepository;
    private final ScopeRepository scopeRepository;
    private final ClientScopeRepository clientScopeRepository;
    private final SecurityAuditService securityAuditService;

    public AdminClientService(
            JpaRegisteredClientRepository jpaRegisteredClientRepository,
            RegisteredClientJpaRepository registeredClientJpaRepository,
            ClientMetadataRepository clientMetadataRepository,
            ScopeRepository scopeRepository,
            ClientScopeRepository clientScopeRepository,
            SecurityAuditService securityAuditService) {
        this.jpaRegisteredClientRepository = jpaRegisteredClientRepository;
        this.registeredClientJpaRepository = registeredClientJpaRepository;
        this.clientMetadataRepository = clientMetadataRepository;
        this.scopeRepository = scopeRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminClientSummary> listClients() {
        return registeredClientJpaRepository.findAll().stream()
                .map(e -> jpaRegisteredClientRepository.findById(e.getId()))
                .filter(rc -> rc != null)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminClientSummary getClient(String clientId) {
        RegisteredClient rc = jpaRegisteredClientRepository.findByClientId(clientId);
        if (rc == null) {
            throw new AdminResourceNotFoundException("Client not found: " + clientId);
        }
        return toSummary(rc);
    }

    @Transactional
    public AdminClientSummary upsertClient(
            AdminClientUpsertRequest request, String actor, HttpServletRequest httpRequest) {
        RegisteredClient existing = jpaRegisteredClientRepository.findByClientId(request.clientId());
        ClientMetadataEntity metadata = clientMetadataRepository.findById(request.clientId()).orElse(null);

        boolean creating = (existing == null);
        if (!creating && metadata != null && metadata.isSystem()) {
            throw new AdminOperationNotAllowedException("System client cannot be modified: " + request.clientId());
        }

        ensureScopesExist(request.scopes(), actor);
        syncClientScopeMapping(request.clientId(), request.scopes());

        boolean rotateSecret = StringUtils.hasText(request.clientSecret());

        RegisteredClient toSave = creating
                ? buildNewRegisteredClient(request)
                : buildUpdatedRegisteredClient(existing, request);

        jpaRegisteredClientRepository.save(toSave);

        ClientMetadataEntity savedMeta = upsertMetadata(metadata, request, actor, creating);

        AuditEventType eventType;
        if (creating) {
            eventType = AuditEventType.CLIENT_REGISTERED;
        } else if (rotateSecret) {
            eventType = AuditEventType.CLIENT_SECRET_ROTATED;
        } else {
            eventType = AuditEventType.CLIENT_UPDATED;
        }

        securityAuditService.recordGenericEvent(
                eventType,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                clientDetails(request.clientId(), savedMeta));

        return toSummary(jpaRegisteredClientRepository.findByClientId(request.clientId()));
    }

    @Transactional
    public void deleteClient(String clientId, String actor, HttpServletRequest httpRequest) {
        ClientMetadataEntity metadata = clientMetadataRepository.findById(clientId).orElse(null);
        if (metadata != null && metadata.isSystem()) {
            throw new AdminOperationNotAllowedException("System client cannot be deleted: " + clientId);
        }

        if (!registeredClientJpaRepository.findByClientId(clientId).isPresent()) {
            throw new AdminResourceNotFoundException("Client not found: " + clientId);
        }

        clientScopeRepository.deleteByIdClientId(clientId);
        clientMetadataRepository.deleteById(clientId);
        registeredClientJpaRepository.deleteByClientId(clientId);

        Map<String, Object> details = new HashMap<>();
        details.put("clientId", clientId);
        securityAuditService.recordGenericEvent(
                AuditEventType.CLIENT_DELETED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);
    }

    @Transactional
    public AdminClientSummary setEnabled(
            String clientId, boolean enabled, String actor, HttpServletRequest httpRequest) {
        ClientMetadataEntity meta = clientMetadataRepository.findById(clientId).orElse(null);
        if (meta == null) {
            // Allow enabling/disabling clients created before metadata existed.
            meta = new ClientMetadataEntity();
            meta.setClientId(clientId);
            meta.setSystem(false);
            meta.setCreatedAt(Instant.now());
            meta.setCreatedBy(actor);
        }

        if (meta.isSystem()) {
            throw new AdminOperationNotAllowedException("System client cannot be disabled/enabled: " + clientId);
        }

        meta.setEnabled(enabled);
        meta.setUpdatedAt(Instant.now());
        clientMetadataRepository.save(meta);

        Map<String, Object> details = new HashMap<>();
        details.put("clientId", clientId);
        details.put("enabled", enabled);

        securityAuditService.recordGenericEvent(
                AuditEventType.CONFIGURATION_CHANGED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);

        return getClient(clientId);
    }

    private AdminClientSummary toSummary(RegisteredClient rc) {
        ClientMetadataEntity meta = clientMetadataRepository.findById(rc.getClientId()).orElse(null);
        boolean enabled = meta == null || meta.isEnabled();
        boolean system = meta != null && meta.isSystem();

        ClientSettings clientSettings = rc.getClientSettings();
        boolean requireProofKey = Boolean.TRUE.equals(clientSettings.getSetting(ClientSettings.REQUIRE_PROOF_KEY));
        boolean requireConsent =
            Boolean.TRUE.equals(
                clientSettings.getSetting(ClientSettings.REQUIRE_AUTHORIZATION_CONSENT));

        return new AdminClientSummary(
                rc.getClientId(),
                rc.getClientName(),
                enabled,
                system,
                List.copyOf(rc.getScopes()),
                rc.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList(),
                rc.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue).toList(),
                List.copyOf(rc.getRedirectUris()),
                List.copyOf(rc.getPostLogoutRedirectUris()),
                requireProofKey,
                requireConsent,
                meta != null ? meta.getNotes() : null);
    }

    private RegisteredClient buildNewRegisteredClient(AdminClientUpsertRequest request) {
        RegisteredClient.Builder builder =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId(request.clientId())
                        .clientIdIssuedAt(Instant.now())
                        .clientName(StringUtils.hasText(request.clientName()) ? request.clientName().trim() : request.clientId());

        if (StringUtils.hasText(request.clientSecret())) {
            builder.clientSecret(request.clientSecret());
        }

        builder.clientAuthenticationMethods(
                methods -> request.clientAuthenticationMethods().forEach(v -> methods.add(resolveClientAuthenticationMethod(v))));
        builder.authorizationGrantTypes(
                types -> request.authorizationGrantTypes().forEach(v -> types.add(resolveAuthorizationGrantType(v))));

        if (request.redirectUris() != null) {
            builder.redirectUris(uris -> uris.addAll(request.redirectUris().stream().filter(StringUtils::hasText).map(String::trim).toList()));
        }
        if (request.postLogoutRedirectUris() != null) {
            builder.postLogoutRedirectUris(
                    uris -> uris.addAll(request.postLogoutRedirectUris().stream().filter(StringUtils::hasText).map(String::trim).toList()));
        }

        builder.scopes(scopes -> scopes.addAll(new LinkedHashSet<>(request.scopes())));

        builder.clientSettings(
                ClientSettings.builder()
                        .requireProofKey(request.requireProofKey())
                        .requireAuthorizationConsent(request.requireAuthorizationConsent())
                        .build());
        builder.tokenSettings(TokenSettings.builder().build());

        return builder.build();
    }

    private RegisteredClient buildUpdatedRegisteredClient(
            RegisteredClient existing, AdminClientUpsertRequest request) {
        RegisteredClient.Builder builder = RegisteredClient.withId(existing.getId())
                .clientId(existing.getClientId())
                .clientIdIssuedAt(existing.getClientIdIssuedAt())
                .clientSecret(existing.getClientSecret())
                .clientSecretExpiresAt(existing.getClientSecretExpiresAt())
                .clientName(StringUtils.hasText(request.clientName()) ? request.clientName().trim() : existing.getClientName());

        if (StringUtils.hasText(request.clientSecret())) {
            builder.clientSecret(request.clientSecret());
        }

        Set<String> authMethods = new LinkedHashSet<>(request.clientAuthenticationMethods());
        builder.clientAuthenticationMethods(
                methods -> authMethods.forEach(v -> methods.add(resolveClientAuthenticationMethod(v))));

        Set<String> grantTypes = new LinkedHashSet<>(request.authorizationGrantTypes());
        builder.authorizationGrantTypes(
                types -> grantTypes.forEach(v -> types.add(resolveAuthorizationGrantType(v))));

        List<String> redirectUris =
                request.redirectUris() != null
                        ? request.redirectUris().stream().filter(StringUtils::hasText).map(String::trim).toList()
                        : List.copyOf(existing.getRedirectUris());
        builder.redirectUris(uris -> uris.addAll(redirectUris));

        List<String> postLogoutRedirectUris =
                request.postLogoutRedirectUris() != null
                        ? request.postLogoutRedirectUris().stream().filter(StringUtils::hasText).map(String::trim).toList()
                        : List.copyOf(existing.getPostLogoutRedirectUris());
        builder.postLogoutRedirectUris(uris -> uris.addAll(postLogoutRedirectUris));

        builder.scopes(scopes -> scopes.addAll(new LinkedHashSet<>(request.scopes())));

        builder.clientSettings(
            ClientSettings.builder()
                .requireProofKey(request.requireProofKey())
                .requireAuthorizationConsent(request.requireAuthorizationConsent())
                .build());
        builder.tokenSettings(existing.getTokenSettings());

        return builder.build();
    }

    private ClientMetadataEntity upsertMetadata(
            ClientMetadataEntity existing,
            AdminClientUpsertRequest request,
            String actor,
            boolean creating) {
        Instant now = Instant.now();
        ClientMetadataEntity meta = existing;
        if (meta == null) {
            meta = new ClientMetadataEntity();
            meta.setClientId(request.clientId());
            meta.setSystem(false);
            meta.setCreatedAt(now);
            meta.setCreatedBy(actor);
        }

        meta.setEnabled(request.enabled());
        meta.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        meta.setUpdatedAt(now);

        // For safety: when updating a pre-seeded record that was marked system=true,
        // we keep that protection.
        if (creating) {
            meta.setSystem(false);
        }

        return clientMetadataRepository.save(meta);
    }

    private void ensureScopesExist(List<String> scopes, String actor) {
        Instant now = Instant.now();
        for (String scope : scopes) {
            if (!StringUtils.hasText(scope)) {
                continue;
            }
            String trimmed = scope.trim();
            ScopeEntity existing = scopeRepository.findById(trimmed).orElse(null);
            if (existing != null) {
                continue;
            }
            ScopeEntity entity = new ScopeEntity();
            entity.setScope(trimmed);
            entity.setDescription(null);
            entity.setEnabled(true);
            entity.setSystem(false);
            entity.setCreatedBy(actor);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            scopeRepository.save(entity);
        }
    }

    private void syncClientScopeMapping(String clientId, List<String> scopes) {
        clientScopeRepository.deleteByIdClientId(clientId);
        Instant now = Instant.now();
        for (String scope : scopes) {
            if (!StringUtils.hasText(scope)) {
                continue;
            }
            ClientScopeEntity e = new ClientScopeEntity();
            ClientScopeId id = new ClientScopeId();
            id.setClientId(clientId);
            id.setScope(scope.trim());
            e.setId(id);
            e.setCreatedAt(now);
            clientScopeRepository.save(e);
        }
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String value) {
        if (!StringUtils.hasText(value)) {
            return new AuthorizationGrantType("");
        }
        String v = value.trim();
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(v)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        }
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(v)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        }
        if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(v)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        return new AuthorizationGrantType(v);
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String value) {
        if (!StringUtils.hasText(value)) {
            return new ClientAuthenticationMethod("");
        }
        String v = value.trim();
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(v)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(v)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }
        if (ClientAuthenticationMethod.NONE.getValue().equals(v)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(v);
    }

    private static Map<String, Object> clientDetails(String clientId, ClientMetadataEntity meta) {
        Map<String, Object> details = new HashMap<>();
        details.put("clientId", clientId);
        if (meta != null) {
            details.put("enabled", meta.isEnabled());
            details.put("system", meta.isSystem());
            if (StringUtils.hasText(meta.getNotes())) {
                details.put("notes", meta.getNotes());
            }
        }
        return details;
    }
}
