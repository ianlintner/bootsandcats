package com.bootsandcats.oauth2.service.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import com.bootsandcats.oauth2.k8s.KubernetesRegisteredClientMapper;
import com.bootsandcats.oauth2.k8s.OAuth2Client;
import com.bootsandcats.oauth2.k8s.OAuth2ClientList;
import com.bootsandcats.oauth2.k8s.OAuth2ClientSpec;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import jakarta.servlet.http.HttpServletRequest;

@Service
@ConditionalOnProperty(prefix = "oauth2.clients", name = "store", havingValue = "kubernetes")
public class KubernetesAdminClientService implements AdminClientOperations {

    private final MixedOperation<OAuth2Client, OAuth2ClientList, Resource<OAuth2Client>> crdClient;
    private final KubernetesRegisteredClientMapper mapper = new KubernetesRegisteredClientMapper();
    private final String namespace;
    private final SecurityAuditService securityAuditService;

    public KubernetesAdminClientService(
            KubernetesClient kubernetesClient,
            @Value("${oauth2.clients.kubernetes.namespace:}") String configuredNamespace,
            SecurityAuditService securityAuditService) {
        this.crdClient = kubernetesClient.resources(OAuth2Client.class, OAuth2ClientList.class);
        this.namespace =
                resolveNamespace(
                        configuredNamespace,
                        kubernetesClient.getNamespace() != null
                                ? kubernetesClient.getNamespace()
                                : null);
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminClientSummary> listClients() {
        return crdClient.inNamespace(namespace).list().getItems().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminClientSummary getClient(String clientId) {
        OAuth2Client resource =
                crdClient
                        .inNamespace(namespace)
                        .withLabels(mapper.selectorForClientId(clientId))
                        .list()
                        .getItems()
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (resource == null) {
            throw new AdminResourceNotFoundException("Client not found: " + clientId);
        }
        return toSummary(resource);
    }

    @Transactional
    public AdminClientSummary upsertClient(
            AdminClientUpsertRequest request, String actor, HttpServletRequest httpRequest) {
        OAuth2Client existing =
                crdClient
                        .inNamespace(namespace)
                        .withLabels(mapper.selectorForClientId(request.clientId()))
                        .list()
                        .getItems()
                        .stream()
                        .findFirst()
                        .orElse(null);

        boolean creating = existing == null;
        OAuth2ClientSpec existingSpec = existing != null ? existing.getSpec() : null;
        if (!creating && existingSpec != null && Boolean.TRUE.equals(existingSpec.getSystem())) {
            throw new AdminOperationNotAllowedException(
                    "System client cannot be modified: " + request.clientId());
        }

        RegisteredClient base =
                creating
                        ? buildNewRegisteredClient(request)
                        : buildUpdatedRegisteredClient(
                                mapper.toRegisteredClient(existing), request);

        OAuth2Client desired = mapper.toResource(base, namespace);
        OAuth2ClientSpec desiredSpec = desired.getSpec();
        desiredSpec.setEnabled(request.enabled());
        desiredSpec.setSystem(existingSpec != null ? existingSpec.getSystem() : Boolean.FALSE);
        desiredSpec.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);

        if (existing != null) {
            desired.getMetadata().setName(existing.getMetadata().getName());
            desired.getMetadata().setResourceVersion(existing.getMetadata().getResourceVersion());
        }

        OAuth2Client saved = crdClient.inNamespace(namespace).resource(desired).createOrReplace();

        AuditEventType eventType;
        if (creating) {
            eventType = AuditEventType.CLIENT_REGISTERED;
        } else if (StringUtils.hasText(request.clientSecret())) {
            eventType = AuditEventType.CLIENT_SECRET_ROTATED;
        } else {
            eventType = AuditEventType.CLIENT_UPDATED;
        }

        securityAuditService.recordGenericEvent(
                eventType,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                clientDetails(request.clientId(), desiredSpec));

        return toSummary(saved);
    }

    @Transactional
    public void deleteClient(String clientId, String actor, HttpServletRequest httpRequest) {
        OAuth2Client resource =
                crdClient
                        .inNamespace(namespace)
                        .withLabels(mapper.selectorForClientId(clientId))
                        .list()
                        .getItems()
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (resource == null) {
            throw new AdminResourceNotFoundException("Client not found: " + clientId);
        }
        if (Boolean.TRUE.equals(resource.getSpec().getSystem())) {
            throw new AdminOperationNotAllowedException(
                    "System client cannot be deleted: " + clientId);
        }

        // In Kubernetes-backed mode, client scopes are stored directly on the OAuth2Client
        // custom resource. No database mapping tables are used.
        crdClient.inNamespace(namespace).withName(resource.getMetadata().getName()).delete();

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
        OAuth2Client resource =
                crdClient
                        .inNamespace(namespace)
                        .withLabels(mapper.selectorForClientId(clientId))
                        .list()
                        .getItems()
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (resource == null) {
            throw new AdminResourceNotFoundException("Client not found: " + clientId);
        }
        if (Boolean.TRUE.equals(resource.getSpec().getSystem())) {
            throw new AdminOperationNotAllowedException(
                    "System client cannot be disabled/enabled: " + clientId);
        }

        resource.getSpec().setEnabled(enabled);
        OAuth2Client updated =
                crdClient.inNamespace(namespace).resource(resource).createOrReplace();

        Map<String, Object> details = new HashMap<>();
        details.put("clientId", clientId);
        details.put("enabled", enabled);

        securityAuditService.recordGenericEvent(
                AuditEventType.CONFIGURATION_CHANGED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);

        return toSummary(updated);
    }

    private AdminClientSummary toSummary(OAuth2Client resource) {
        RegisteredClient rc = mapper.toRegisteredClient(resource);
        OAuth2ClientSpec spec = resource.getSpec();
        boolean enabled = spec.getEnabled() == null || spec.getEnabled();
        boolean system = spec.getSystem() != null && spec.getSystem();

        ClientSettings clientSettings = rc.getClientSettings();
        boolean requireProofKey = clientSettings.isRequireProofKey();
        boolean requireConsent = clientSettings.isRequireAuthorizationConsent();

        return new AdminClientSummary(
                rc.getClientId(),
                rc.getClientName(),
                enabled,
                system,
                List.copyOf(rc.getScopes()),
                rc.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue)
                        .toList(),
                rc.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue)
                        .toList(),
                List.copyOf(rc.getRedirectUris()),
                List.copyOf(rc.getPostLogoutRedirectUris()),
                requireProofKey,
                requireConsent,
                spec.getNotes());
    }

    private RegisteredClient buildNewRegisteredClient(AdminClientUpsertRequest request) {
        RegisteredClient.Builder builder =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId(request.clientId())
                        .clientIdIssuedAt(Instant.now())
                        .clientName(
                                StringUtils.hasText(request.clientName())
                                        ? request.clientName().trim()
                                        : request.clientId());

        if (StringUtils.hasText(request.clientSecret())) {
            builder.clientSecret(request.clientSecret());
        }

        builder.clientAuthenticationMethods(
                methods ->
                        request.clientAuthenticationMethods()
                                .forEach(v -> methods.add(resolveClientAuthenticationMethod(v))));
        builder.authorizationGrantTypes(
                types ->
                        request.authorizationGrantTypes()
                                .forEach(v -> types.add(resolveAuthorizationGrantType(v))));

        if (request.redirectUris() != null) {
            builder.redirectUris(
                    uris ->
                            uris.addAll(
                                    request.redirectUris().stream()
                                            .filter(StringUtils::hasText)
                                            .map(String::trim)
                                            .toList()));
        }
        if (request.postLogoutRedirectUris() != null) {
            builder.postLogoutRedirectUris(
                    uris ->
                            uris.addAll(
                                    request.postLogoutRedirectUris().stream()
                                            .filter(StringUtils::hasText)
                                            .map(String::trim)
                                            .toList()));
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
        RegisteredClient.Builder builder =
                RegisteredClient.withId(existing.getId())
                        .clientId(existing.getClientId())
                        .clientIdIssuedAt(existing.getClientIdIssuedAt())
                        .clientSecret(existing.getClientSecret())
                        .clientSecretExpiresAt(existing.getClientSecretExpiresAt())
                        .clientName(
                                StringUtils.hasText(request.clientName())
                                        ? request.clientName().trim()
                                        : existing.getClientName());

        if (StringUtils.hasText(request.clientSecret())) {
            builder.clientSecret(request.clientSecret());
        }

        Set<String> authMethods = new LinkedHashSet<>(request.clientAuthenticationMethods());
        builder.clientAuthenticationMethods(
                methods ->
                        authMethods.forEach(
                                v -> methods.add(resolveClientAuthenticationMethod(v))));

        Set<String> grantTypes = new LinkedHashSet<>(request.authorizationGrantTypes());
        builder.authorizationGrantTypes(
                types -> grantTypes.forEach(v -> types.add(resolveAuthorizationGrantType(v))));

        List<String> redirectUris =
                request.redirectUris() != null
                        ? request.redirectUris().stream()
                                .filter(StringUtils::hasText)
                                .map(String::trim)
                                .toList()
                        : List.copyOf(existing.getRedirectUris());
        builder.redirectUris(uris -> uris.addAll(redirectUris));

        List<String> postLogoutRedirectUris =
                request.postLogoutRedirectUris() != null
                        ? request.postLogoutRedirectUris().stream()
                                .filter(StringUtils::hasText)
                                .map(String::trim)
                                .toList()
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

    private static Map<String, Object> clientDetails(String clientId, OAuth2ClientSpec spec) {
        Map<String, Object> details = new HashMap<>();
        details.put("clientId", clientId);
        if (spec != null) {
            details.put("enabled", spec.getEnabled());
            details.put("system", spec.getSystem());
            if (StringUtils.hasText(spec.getNotes())) {
                details.put("notes", spec.getNotes());
            }
        }
        return details;
    }

    private static String resolveNamespace(String configured, String fromClient) {
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        if (StringUtils.hasText(fromClient)) {
            return fromClient;
        }
        String fromEnv = System.getenv("POD_NAMESPACE");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv;
        }
        return "default";
    }
}
