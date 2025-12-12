package com.bootsandcats.oauth2.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class ClientScopeId implements Serializable {

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "scope", length = 200)
    private String scope;
}
