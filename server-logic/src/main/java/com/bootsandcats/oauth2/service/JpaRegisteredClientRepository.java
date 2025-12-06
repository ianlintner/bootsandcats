package com.bootsandcats.oauth2.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.model.RegisteredClientEntity;
import com.bootsandcats.oauth2.repository.RegisteredClientJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final RegisteredClientJpaRepository registeredClientJpaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpaRegisteredClientRepository(
            RegisteredClientJpaRepository registeredClientJpaRepository) {
        this.registeredClientJpaRepository = registeredClientJpaRepository;

        ClassLoader classLoader = JpaRegisteredClientRepository.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        this.objectMapper.registerModules(securityModules);
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        this.registeredClientJpaRepository.save(toEntity(registeredClient));
    }

    @Override
    public RegisteredClient findById(String id) {
        return this.registeredClientJpaRepository.findById(id).map(this::toObject).orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return this.registeredClientJpaRepository
                .findByClientId(clientId)
                .map(this::toObject)
                .orElse(null);
    }

    private RegisteredClientEntity toEntity(RegisteredClient registeredClient) {
        RegisteredClientEntity entity = new RegisteredClientEntity();
        entity.setId(registeredClient.getId());
        entity.setClientId(registeredClient.getClientId());
        entity.setClientIdIssuedAt(registeredClient.getClientIdIssuedAt());
        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        entity.setClientName(registeredClient.getClientName());

        entity.setClientAuthenticationMethods(
                StringUtils.collectionToCommaDelimitedString(
                        registeredClient.getClientAuthenticationMethods().stream()
                                .map(ClientAuthenticationMethod::getValue)
                                .collect(Collectors.toSet())));

        entity.setAuthorizationGrantTypes(
                StringUtils.collectionToCommaDelimitedString(
                        registeredClient.getAuthorizationGrantTypes().stream()
                                .map(AuthorizationGrantType::getValue)
                                .collect(Collectors.toSet())));

        entity.setRedirectUris(
                StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        entity.setPostLogoutRedirectUris(
                StringUtils.collectionToCommaDelimitedString(
                        registeredClient.getPostLogoutRedirectUris()));
        entity.setScopes(
                StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));

        entity.setClientSettings(writeMap(registeredClient.getClientSettings().getSettings()));
        entity.setTokenSettings(writeMap(registeredClient.getTokenSettings().getSettings()));

        return entity;
    }

    private RegisteredClient toObject(RegisteredClientEntity entity) {
        Set<String> clientAuthenticationMethods =
                StringUtils.commaDelimitedListToSet(entity.getClientAuthenticationMethods());
        Set<String> authorizationGrantTypes =
                StringUtils.commaDelimitedListToSet(entity.getAuthorizationGrantTypes());
        Set<String> redirectUris = StringUtils.commaDelimitedListToSet(entity.getRedirectUris());
        Set<String> postLogoutRedirectUris =
                StringUtils.commaDelimitedListToSet(entity.getPostLogoutRedirectUris());
        Set<String> scopes = StringUtils.commaDelimitedListToSet(entity.getScopes());

        RegisteredClient.Builder builder =
                RegisteredClient.withId(entity.getId())
                        .clientId(entity.getClientId())
                        .clientIdIssuedAt(entity.getClientIdIssuedAt())
                        .clientSecret(entity.getClientSecret())
                        .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                        .clientName(entity.getClientName())
                        .clientAuthenticationMethods(
                                methods ->
                                        clientAuthenticationMethods.forEach(
                                                method ->
                                                        methods.add(
                                                                resolveClientAuthenticationMethod(
                                                                        method))))
                        .authorizationGrantTypes(
                                types ->
                                        authorizationGrantTypes.forEach(
                                                type ->
                                                        types.add(
                                                                resolveAuthorizationGrantType(
                                                                        type))))
                        .redirectUris(uris -> uris.addAll(redirectUris))
                        .postLogoutRedirectUris(uris -> uris.addAll(postLogoutRedirectUris))
                        .scopes(s -> s.addAll(scopes));

        Map<String, Object> clientSettingsMap = parseMap(entity.getClientSettings());
        builder.clientSettings(ClientSettings.withSettings(clientSettingsMap).build());

        Map<String, Object> tokenSettingsMap = parseMap(entity.getTokenSettings());
        builder.tokenSettings(TokenSettings.withSettings(tokenSettingsMap).build());

        return builder.build();
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return this.objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> parseMap(String data) {
        try {
            return this.objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(
            String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        } else if (AuthorizationGrantType.CLIENT_CREDENTIALS
                .getValue()
                .equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        return new AuthorizationGrantType(authorizationGrantType);
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(
            String clientAuthenticationMethod) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                .getValue()
                .equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST
                .getValue()
                .equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else if (ClientAuthenticationMethod.NONE.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(clientAuthenticationMethod);
    }
}
