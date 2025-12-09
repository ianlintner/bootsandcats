package com.bootsandcats.profileui.events;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Representation of an auth activity event consumed from Redis Streams. */
public class AuthActivityEvent {

    private String eventId;
    private String eventType;
    private String eventCategory;
    private Instant eventTimestamp;
    private String principal;
    private String principalType;
    private Long userId;
    private String clientId;
    private String scopes;
    private String result;
    private String grantType;
    private String tokenType;
    private String detailsJson;
    private Map<String, Object> details = Collections.emptyMap();

    public static AuthActivityEvent fromMap(Map<String, String> values, ObjectMapper mapper) {
        AuthActivityEvent event = new AuthActivityEvent();
        event.setEventId(values.get("eventId"));
        event.setEventType(values.get("eventType"));
        event.setEventCategory(values.get("eventCategory"));
        event.setPrincipal(values.get("principal"));
        event.setPrincipalType(values.get("principalType"));
        event.setClientId(values.get("clientId"));
        event.setScopes(values.get("scopes"));
        event.setResult(values.get("result"));
        event.setGrantType(values.get("grantType"));
        event.setTokenType(values.get("tokenType"));
        event.setDetailsJson(values.get("details"));

        String timestamp = values.get("eventTimestamp");
        if (timestamp != null) {
            try {
                event.setEventTimestamp(Instant.parse(timestamp));
            } catch (Exception ignored) {
                // Leave timestamp null if parsing fails
            }
        }

        String userId = values.get("userId");
        if (userId != null) {
            try {
                event.setUserId(Long.parseLong(userId));
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }

        if (event.detailsJson != null && mapper != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(event.detailsJson, Map.class);
                event.setDetails(parsed);
            } catch (Exception ignored) {
                // Keep empty map on parse failure
            }
        }

        return event;
    }

    public boolean isLoginSuccess() {
        return "LOGIN_SUCCESS".equalsIgnoreCase(eventType)
                || "FEDERATED_LOGIN_SUCCESS".equalsIgnoreCase(eventType);
    }

    public Optional<String> detail(String key) {
        Object value = details.get(key);
        return value != null ? Optional.of(value.toString()) : Optional.empty();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details != null ? new HashMap<>(details) : Collections.emptyMap();
    }
}
