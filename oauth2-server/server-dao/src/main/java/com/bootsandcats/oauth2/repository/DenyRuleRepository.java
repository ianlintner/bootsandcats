package com.bootsandcats.oauth2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyRuleEntity;

public interface DenyRuleRepository extends JpaRepository<DenyRuleEntity, Long> {

    @Query(
            """
            select r from DenyRuleEntity r
            where r.enabled = true
              and r.matchField = :matchField
              and (r.provider is null or lower(r.provider) = lower(:provider) or r.provider = '*')
            """)
    List<DenyRuleEntity> findActiveRulesForProvider(
            @Param("provider") String provider, @Param("matchField") DenyMatchField matchField);

    @Query(
            """
            select r from DenyRuleEntity r
            where r.enabled = true
              and r.matchField = :matchField
              and r.provider is null
            """)
    List<DenyRuleEntity> findActiveGlobalRules(@Param("matchField") DenyMatchField matchField);
}
