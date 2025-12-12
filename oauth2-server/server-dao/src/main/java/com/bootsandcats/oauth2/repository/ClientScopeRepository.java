package com.bootsandcats.oauth2.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bootsandcats.oauth2.model.ClientScopeEntity;
import com.bootsandcats.oauth2.model.ClientScopeId;

public interface ClientScopeRepository extends JpaRepository<ClientScopeEntity, ClientScopeId> {

    List<ClientScopeEntity> findByIdClientId(String clientId);

    long deleteByIdClientId(String clientId);

    @Query("select distinct cs.id.clientId from ClientScopeEntity cs where cs.id.scope = :scope")
    List<String> findClientIdsByScope(@Param("scope") String scope);
}
