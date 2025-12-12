package com.bootsandcats.oauth2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bootsandcats.oauth2.model.ScopeEntity;

public interface ScopeRepository extends JpaRepository<ScopeEntity, String> {}
