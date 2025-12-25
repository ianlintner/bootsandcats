package com.bootsandcats.oauth2.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bootsandcats.oauth2.dto.admin.AdminClientSummary;
import com.bootsandcats.oauth2.dto.admin.AdminClientUpsertRequest;
import com.bootsandcats.oauth2.service.admin.AdminClientOperations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/clients")
@Validated
public class AdminClientAdminController {

    private final AdminClientOperations adminClientService;

    public AdminClientAdminController(AdminClientOperations adminClientService) {
        this.adminClientService = adminClientService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminClientSummary>> listClients() {
        return ResponseEntity.ok(adminClientService.listClients());
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientSummary> getClient(@PathVariable String clientId) {
        return ResponseEntity.ok(adminClientService.getClient(clientId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientSummary> createClient(
            @Valid @RequestBody AdminClientUpsertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        // Upsert allows create; service enforces system protection.
        return ResponseEntity.ok(
                adminClientService.upsertClient(request, authentication.getName(), httpRequest));
    }

    @PutMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientSummary> updateClient(
            @PathVariable String clientId,
            @Valid @RequestBody AdminClientUpsertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        if (!clientId.equals(request.clientId())) {
            throw new IllegalArgumentException("Path clientId must match request.clientId");
        }
        return ResponseEntity.ok(
                adminClientService.upsertClient(request, authentication.getName(), httpRequest));
    }

    @PostMapping("/{clientId}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminClientSummary> setEnabled(
            @PathVariable String clientId,
            @RequestParam boolean enabled,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminClientService.setEnabled(
                        clientId, enabled, authentication.getName(), httpRequest));
    }

    @DeleteMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClient(
            @PathVariable String clientId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        adminClientService.deleteClient(clientId, authentication.getName(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
