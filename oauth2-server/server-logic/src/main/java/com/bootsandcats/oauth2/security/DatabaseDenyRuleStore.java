package com.bootsandcats.oauth2.security;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyRuleEntity;
import com.bootsandcats.oauth2.repository.DenyRuleRepository;

/** Database-backed deny rule store (default). */
@Service
@ConditionalOnProperty(
        prefix = "oauth2.deny",
        name = "store",
        havingValue = "database",
        matchIfMissing = true)
public class DatabaseDenyRuleStore implements DenyRuleStore {

    private final DenyRuleRepository denyRuleRepository;

    public DatabaseDenyRuleStore(DenyRuleRepository denyRuleRepository) {
        this.denyRuleRepository = denyRuleRepository;
    }

    @Override
    public List<DenyRuleEntity> findAll() {
        return denyRuleRepository.findAll();
    }

    @Override
    public Optional<DenyRuleEntity> findById(long id) {
        return denyRuleRepository.findById(id);
    }

    @Override
    public DenyRuleEntity save(DenyRuleEntity entity) {
        return denyRuleRepository.save(entity);
    }

    @Override
    public void delete(DenyRuleEntity entity) {
        denyRuleRepository.delete(entity);
    }

    @Override
    public List<DenyRuleEntity> findActiveRulesForProvider(
            String provider, DenyMatchField matchField) {
        return denyRuleRepository.findActiveRulesForProvider(provider, matchField);
    }
}
