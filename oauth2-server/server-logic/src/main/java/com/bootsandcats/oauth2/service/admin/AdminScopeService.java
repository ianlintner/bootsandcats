package com.bootsandcats.oauth2.service.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.dto.admin.AdminScopeSummary;
import com.bootsandcats.oauth2.dto.admin.AdminScopeUpsertRequest;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.ScopeEntity;
import com.bootsandcats.oauth2.repository.ClientScopeRepository;
import com.bootsandcats.oauth2.repository.ScopeRepository;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.http.HttpServletRequest;

@Service
@Profile("!prod-no-db")
public class AdminScopeService {

    private final ScopeRepository scopeRepository;
    private final ClientScopeRepository clientScopeRepository;
    private final SecurityAuditService securityAuditService;

    public AdminScopeService(
            ScopeRepository scopeRepository,
            ClientScopeRepository clientScopeRepository,
            SecurityAuditService securityAuditService) {
        this.scopeRepository = scopeRepository;
        this.clientScopeRepository = clientScopeRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminScopeSummary> listScopes() {
        return scopeRepository.findAll().stream()
                .map(
                        e ->
                                new AdminScopeSummary(
                                        e.getScope(),
                                        e.getDescription(),
                                        e.isEnabled(),
                                        e.isSystem()))
                .toList();
    }

    @Transactional
    public AdminScopeSummary upsertScope(
            AdminScopeUpsertRequest request, String actor, HttpServletRequest httpRequest) {
        ScopeEntity entity = scopeRepository.findById(request.scope()).orElse(null);
        Instant now = Instant.now();

        boolean creating = (entity == null);
        if (creating) {
            entity = new ScopeEntity();
            entity.setScope(request.scope());
            entity.setSystem(false);
            entity.setCreatedAt(now);
            entity.setCreatedBy(actor);
        } else {
            // System scopes are protected from being disabled/renamed.
            if (entity.isSystem() && !request.enabled()) {
                throw new AdminOperationNotAllowedException(
                        "System scope cannot be disabled: " + entity.getScope());
            }
        }

        entity.setDescription(
                StringUtils.hasText(request.description()) ? request.description().trim() : null);
        entity.setEnabled(request.enabled());
        entity.setUpdatedAt(now);

        ScopeEntity saved = scopeRepository.save(entity);

        Map<String, Object> details = new HashMap<>();
        details.put("scope", saved.getScope());
        details.put("enabled", saved.isEnabled());
        if (StringUtils.hasText(saved.getDescription())) {
            details.put("description", saved.getDescription());
        }

        securityAuditService.recordGenericEvent(
                creating ? AuditEventType.SCOPE_CREATED : AuditEventType.CONFIGURATION_CHANGED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);

        return new AdminScopeSummary(
                saved.getScope(), saved.getDescription(), saved.isEnabled(), saved.isSystem());
    }

    @Transactional
    public void deleteScope(String scope, String actor, HttpServletRequest httpRequest) {
        ScopeEntity existing = scopeRepository.findById(scope).orElse(null);
        if (existing == null) {
            throw new AdminResourceNotFoundException("Scope not found: " + scope);
        }
        if (existing.isSystem()) {
            throw new AdminOperationNotAllowedException("System scope cannot be deleted: " + scope);
        }

        List<String> clientIds = clientScopeRepository.findClientIdsByScope(scope);
        if (!clientIds.isEmpty()) {
            throw new AdminOperationNotAllowedException(
                    "Cannot delete scope that is assigned to clients: " + scope);
        }

        scopeRepository.delete(existing);

        Map<String, Object> details = new HashMap<>();
        details.put("scope", scope);

        securityAuditService.recordGenericEvent(
                AuditEventType.SCOPE_DELETED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);
    }
}
