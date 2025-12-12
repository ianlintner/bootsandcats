package com.bootsandcats.oauth2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bootsandcats.oauth2.model.ClientScopeEntity;
import com.bootsandcats.oauth2.model.ClientScopeId;

public interface ClientScopeRepository extends JpaRepository<ClientScopeEntity, ClientScopeId> {}
