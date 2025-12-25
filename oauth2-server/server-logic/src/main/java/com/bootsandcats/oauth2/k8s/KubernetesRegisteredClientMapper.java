package com.bootsandcats.oauth2.k8s;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

import io.fabric8.kubernetes.api.model.ObjectMeta;

/**
 * Maps between Spring {@link RegisteredClient} objects and the Kubernetes custom resource model.
 */
public class KubernetesRegisteredClientMapper {

    private static final String LABEL_CLIENT_ID = "oauth.bootsandcats.com/client-id";
    private static final String LABEL_REGISTERED_CLIENT_ID =
            "oauth.bootsandcats.com/registered-client-id";

    public OAuth2Client toResource(RegisteredClient registeredClient, String namespace) {
        OAuth2Client resource = new OAuth2Client();

        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(namespace);
        meta.setName(toResourceName(registeredClient.getClientId()));

        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_CLIENT_ID, safeLabelValue(registeredClient.getClientId()));
        labels.put(LABEL_REGISTERED_CLIENT_ID, safeLabelValue(registeredClient.getId()));
        meta.setLabels(labels);
        resource.setMetadata(meta);

        OAuth2ClientSpec spec = new OAuth2ClientSpec();
        spec.setRegisteredClientId(registeredClient.getId());
        spec.setClientId(registeredClient.getClientId());
        spec.setClientName(registeredClient.getClientName());
        spec.setClientIdIssuedAt(
                registeredClient.getClientIdIssuedAt() != null
                        ? registeredClient.getClientIdIssuedAt().toString()
                        : null);
        spec.setClientSecretExpiresAt(
                registeredClient.getClientSecretExpiresAt() != null
                        ? registeredClient.getClientSecretExpiresAt().toString()
                        : null);
        spec.setEnabled(true);
        spec.setSystem(false);
        spec.setEncodedSecret(registeredClient.getClientSecret());
        spec.setClientAuthenticationMethods(
                registeredClient.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue)
                        .toList());
        spec.setAuthorizationGrantTypes(
                registeredClient.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue)
                        .toList());
        spec.setRedirectUris(new ArrayList<>(registeredClient.getRedirectUris()));
        spec.setPostLogoutRedirectUris(
                new ArrayList<>(registeredClient.getPostLogoutRedirectUris()));
        spec.setScopes(new ArrayList<>(registeredClient.getScopes()));

        OAuth2ClientSettings settings = new OAuth2ClientSettings();
        settings.setRequireProofKey(registeredClient.getClientSettings().isRequireProofKey());
        settings.setRequireAuthorizationConsent(
                registeredClient.getClientSettings().isRequireAuthorizationConsent());
        spec.setClientSettings(settings);

        OAuth2ClientTokenSettings tokenSettings = new OAuth2ClientTokenSettings();
        TokenSettings ts = registeredClient.getTokenSettings();
        if (ts.getAccessTokenTimeToLive() != null) {
            tokenSettings.setAccessTokenTtl(ts.getAccessTokenTimeToLive().toString());
        }
        if (ts.getRefreshTokenTimeToLive() != null) {
            tokenSettings.setRefreshTokenTtl(ts.getRefreshTokenTimeToLive().toString());
        }
        if (ts.getAuthorizationCodeTimeToLive() != null) {
            tokenSettings.setAuthorizationCodeTtl(ts.getAuthorizationCodeTimeToLive().toString());
        }
        tokenSettings.setReuseRefreshTokens(ts.isReuseRefreshTokens());
        spec.setTokenSettings(tokenSettings);

        resource.setSpec(spec);
        return resource;
    }

    public RegisteredClient toRegisteredClient(OAuth2Client resource) {
        if (resource == null || resource.getSpec() == null) {
            return null;
        }
        OAuth2ClientSpec spec = resource.getSpec();
        String registeredClientId =
                StringUtils.hasText(spec.getRegisteredClientId())
                        ? spec.getRegisteredClientId()
                        : resource.getMetadata().getUid();

        RegisteredClient.Builder builder =
                RegisteredClient.withId(registeredClientId)
                        .clientId(spec.getClientId())
                        .clientSecret(spec.getEncodedSecret())
                        .clientName(
                                StringUtils.hasText(spec.getClientName())
                                        ? spec.getClientName()
                                        : spec.getClientId());

        if (StringUtils.hasText(spec.getClientIdIssuedAt())) {
            builder.clientIdIssuedAt(Instant.parse(spec.getClientIdIssuedAt()));
        }
        if (StringUtils.hasText(spec.getClientSecretExpiresAt())) {
            builder.clientSecretExpiresAt(Instant.parse(spec.getClientSecretExpiresAt()));
        }

        builder.clientAuthenticationMethods(
                methods -> {
                    for (String method : safeList(spec.getClientAuthenticationMethods())) {
                        methods.add(resolveClientAuthenticationMethod(method));
                    }
                });

        builder.authorizationGrantTypes(
                grantTypes -> {
                    for (String gt : safeList(spec.getAuthorizationGrantTypes())) {
                        grantTypes.add(resolveAuthorizationGrantType(gt));
                    }
                });

        builder.redirectUris(uris -> uris.addAll(safeList(spec.getRedirectUris())));
        builder.postLogoutRedirectUris(
                uris -> uris.addAll(safeList(spec.getPostLogoutRedirectUris())));
        builder.scopes(scopes -> scopes.addAll(new ArrayList<>(safeList(spec.getScopes()))));

        OAuth2ClientSettings cs =
                spec.getClientSettings() != null
                        ? spec.getClientSettings()
                        : new OAuth2ClientSettings();
        builder.clientSettings(
                ClientSettings.builder()
                        .requireProofKey(Boolean.TRUE.equals(cs.getRequireProofKey()))
                        .requireAuthorizationConsent(
                                Boolean.TRUE.equals(cs.getRequireAuthorizationConsent()))
                        .build());

        OAuth2ClientTokenSettings ts =
                spec.getTokenSettings() != null
                        ? spec.getTokenSettings()
                        : new OAuth2ClientTokenSettings();
        TokenSettings.Builder tokenBuilder = TokenSettings.builder();
        if (StringUtils.hasText(ts.getAccessTokenTtl())) {
            tokenBuilder.accessTokenTimeToLive(Duration.parse(ts.getAccessTokenTtl()));
        }
        if (StringUtils.hasText(ts.getRefreshTokenTtl())) {
            tokenBuilder.refreshTokenTimeToLive(Duration.parse(ts.getRefreshTokenTtl()));
        }
        if (StringUtils.hasText(ts.getAuthorizationCodeTtl())) {
            tokenBuilder.authorizationCodeTimeToLive(Duration.parse(ts.getAuthorizationCodeTtl()));
        }
        if (ts.getReuseRefreshTokens() != null) {
            tokenBuilder.reuseRefreshTokens(ts.getReuseRefreshTokens());
        }
        builder.tokenSettings(tokenBuilder.build());

        return builder.build();
    }

    public String toResourceName(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return "client-unknown";
        }
        String normalized = clientId.toLowerCase().replaceAll("[^a-z0-9-]+", "-");
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63);
        }
        if (!StringUtils.hasText(normalized)) {
            return "client-unknown";
        }
        return normalized;
    }

    public Map<String, String> selectorForClientId(String clientId) {
        Map<String, String> selector = new HashMap<>();
        selector.put(LABEL_CLIENT_ID, safeLabelValue(clientId));
        return selector;
    }

    public Map<String, String> selectorForRegisteredClientId(String registeredClientId) {
        Map<String, String> selector = new HashMap<>();
        selector.put(LABEL_REGISTERED_CLIENT_ID, safeLabelValue(registeredClientId));
        return selector;
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String value) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(value)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(value)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }
        if (ClientAuthenticationMethod.NONE.getValue().equals(value)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(value);
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String value) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(value)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        }
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(value)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        }
        if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(value)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        return new AuthorizationGrantType(value);
    }

    private static List<String> safeList(List<String> value) {
        return value != null ? value : List.of();
    }

    private static String safeLabelValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "empty";
        }
        String sanitized =
                value.toLowerCase()
                        .chars()
                        .mapToObj(
                                c -> Character.isLetterOrDigit(c) ? String.valueOf((char) c) : "-")
                        .collect(Collectors.joining());
        if (sanitized.length() > 63) {
            sanitized = sanitized.substring(0, 63);
        }
        if (!StringUtils.hasText(sanitized)) {
            return "empty";
        }
        return sanitized;
    }
}
