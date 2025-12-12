package com.bootsandcats.oauth2.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "oauth2_client_metadata")
public class ClientMetadataEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
