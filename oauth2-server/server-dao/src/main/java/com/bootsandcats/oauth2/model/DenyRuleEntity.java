package com.bootsandcats.oauth2.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "security_deny_rule")
public class DenyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Nullable: when null, rule applies to all providers. */
    @Column(name = "provider")
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false)
    private DenyMatchField matchField;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private DenyMatchType matchType;

    @Column(name = "pattern", nullable = false)
    private String pattern;

    /** Normalized value to support fast EXACT matches (e.g., lowercased email/username). */
    @Column(name = "normalized_value")
    private String normalizedValue;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
