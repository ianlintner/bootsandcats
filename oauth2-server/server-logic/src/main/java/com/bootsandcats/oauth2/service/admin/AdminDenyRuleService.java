package com.bootsandcats.oauth2.service.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.dto.admin.AdminDenyRuleSummary;
import com.bootsandcats.oauth2.dto.admin.AdminDenyRuleUpsertRequest;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.DenyMatchType;
import com.bootsandcats.oauth2.model.DenyRuleEntity;
import com.bootsandcats.oauth2.repository.DenyRuleRepository;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminDenyRuleService {

    private final DenyRuleRepository denyRuleRepository;
    private final SecurityAuditService securityAuditService;

    public AdminDenyRuleService(
            DenyRuleRepository denyRuleRepository, SecurityAuditService securityAuditService) {
        this.denyRuleRepository = denyRuleRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminDenyRuleSummary> listRules() {
        return denyRuleRepository.findAll().stream().map(AdminDenyRuleService::toSummary).toList();
    }

    @Transactional
    public AdminDenyRuleSummary createRule(
            AdminDenyRuleUpsertRequest request, String actor, HttpServletRequest httpRequest) {
        DenyRuleEntity entity = new DenyRuleEntity();
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(actor);
        apply(entity, request);

        DenyRuleEntity saved = denyRuleRepository.save(entity);

        securityAuditService.recordGenericEvent(
                AuditEventType.DENY_RULE_CREATED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details(saved));

        return toSummary(saved);
    }

    @Transactional
    public AdminDenyRuleSummary updateRule(
            long id,
            AdminDenyRuleUpsertRequest request,
            String actor,
            HttpServletRequest httpRequest) {
        DenyRuleEntity entity =
                denyRuleRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new AdminResourceNotFoundException(
                                                "Deny rule not found: " + id));

        entity.setUpdatedAt(Instant.now());
        apply(entity, request);

        DenyRuleEntity saved = denyRuleRepository.save(entity);

        securityAuditService.recordGenericEvent(
                AuditEventType.DENY_RULE_UPDATED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details(saved));

        return toSummary(saved);
    }

    @Transactional
    public void deleteRule(long id, String actor, HttpServletRequest httpRequest) {
        DenyRuleEntity entity =
                denyRuleRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new AdminResourceNotFoundException(
                                                "Deny rule not found: " + id));
        denyRuleRepository.delete(entity);

        Map<String, Object> details = new HashMap<>();
        details.put("id", id);

        securityAuditService.recordGenericEvent(
                AuditEventType.DENY_RULE_DELETED,
                AuditEventResult.SUCCESS,
                actor,
                httpRequest,
                details);
    }

    private void apply(DenyRuleEntity entity, AdminDenyRuleUpsertRequest request) {
        entity.setEnabled(request.enabled());

        String provider =
                StringUtils.hasText(request.provider()) ? request.provider().trim() : null;
        entity.setProvider(provider);

        entity.setMatchField(request.matchField());
        entity.setMatchType(request.matchType());

        String pattern = request.pattern().trim();
        entity.setPattern(pattern);

        if (request.matchType() == DenyMatchType.EXACT) {
            entity.setNormalizedValue(pattern.trim().toLowerCase(Locale.ROOT));
        } else {
            entity.setNormalizedValue(null);
        }

        entity.setReason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null);
    }

    private static AdminDenyRuleSummary toSummary(DenyRuleEntity e) {
        return new AdminDenyRuleSummary(
                e.getId(),
                e.isEnabled(),
                e.getProvider(),
                e.getMatchField(),
                e.getMatchType(),
                e.getPattern(),
                e.getReason());
    }

    private static Map<String, Object> details(DenyRuleEntity e) {
        Map<String, Object> details = new HashMap<>();
        details.put("id", e.getId());
        details.put("enabled", e.isEnabled());
        if (StringUtils.hasText(e.getProvider())) {
            details.put("provider", e.getProvider());
        }
        details.put("matchField", e.getMatchField().name());
        details.put("matchType", e.getMatchType().name());
        details.put("pattern", e.getPattern());
        if (StringUtils.hasText(e.getReason())) {
            details.put("reason", e.getReason());
        }
        return details;
    }
}
