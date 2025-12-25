package com.bootsandcats.oauth2.security;

import java.util.List;
import java.util.Optional;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyRuleEntity;

/**
 * Abstraction over where deny rules are stored (database or Kubernetes CRD).
 */
public interface DenyRuleStore {

    List<DenyRuleEntity> findAll();

    Optional<DenyRuleEntity> findById(long id);

    /**
     * Save (create/update) a rule.
     *
     * <p>For new rules, the returned entity must have a non-null {@code id}.
     */
    DenyRuleEntity save(DenyRuleEntity entity);

    void delete(DenyRuleEntity entity);

    List<DenyRuleEntity> findActiveRulesForProvider(String provider, DenyMatchField matchField);
}
