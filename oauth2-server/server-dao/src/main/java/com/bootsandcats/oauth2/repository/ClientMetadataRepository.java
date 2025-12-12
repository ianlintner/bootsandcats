package com.bootsandcats.oauth2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bootsandcats.oauth2.model.ClientMetadataEntity;

public interface ClientMetadataRepository extends JpaRepository<ClientMetadataEntity, String> {}
