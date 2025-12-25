package com.bootsandcats.oauth2.service.admin;

import java.util.List;

import com.bootsandcats.oauth2.dto.admin.AdminClientSummary;
import com.bootsandcats.oauth2.dto.admin.AdminClientUpsertRequest;

import jakarta.servlet.http.HttpServletRequest;

public interface AdminClientOperations {
    List<AdminClientSummary> listClients();

    AdminClientSummary getClient(String clientId);

    AdminClientSummary upsertClient(
            AdminClientUpsertRequest request, String actor, HttpServletRequest httpRequest);

    AdminClientSummary setEnabled(
            String clientId, boolean enabled, String actor, HttpServletRequest httpRequest);

    void deleteClient(String clientId, String actor, HttpServletRequest httpRequest);
}
