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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bootsandcats.oauth2.dto.admin.AdminScopeSummary;
import com.bootsandcats.oauth2.dto.admin.AdminScopeUpsertRequest;
import com.bootsandcats.oauth2.service.admin.AdminScopeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/scopes")
@Validated
public class AdminScopeAdminController {

    private final AdminScopeService adminScopeService;

    public AdminScopeAdminController(AdminScopeService adminScopeService) {
        this.adminScopeService = adminScopeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminScopeSummary>> listScopes() {
        return ResponseEntity.ok(adminScopeService.listScopes());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminScopeSummary> upsertScope(
            @Valid @RequestBody AdminScopeUpsertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminScopeService.upsertScope(request, authentication.getName(), httpRequest));
    }

    @DeleteMapping("/{scope}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteScope(
            @PathVariable String scope,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        adminScopeService.deleteScope(scope, authentication.getName(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
