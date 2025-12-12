package com.bootsandcats.oauth2.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bootsandcats.oauth2.model.RegisteredClientEntity;

@Repository
public interface RegisteredClientJpaRepository
        extends JpaRepository<RegisteredClientEntity, String> {
    Optional<RegisteredClientEntity> findByClientId(String clientId);

    void deleteByClientId(String clientId);
}
