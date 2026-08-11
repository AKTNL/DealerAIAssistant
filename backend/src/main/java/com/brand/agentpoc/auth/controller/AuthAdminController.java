package com.brand.agentpoc.auth.controller;

import com.brand.agentpoc.auth.application.AuthAdministrationService;
import com.brand.agentpoc.auth.application.AuthAdministrationService.RoleView;
import com.brand.agentpoc.auth.application.AuthAdministrationService.UserView;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.dto.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AuthAdminController {

    private final AuthAdministrationService administrationService;

    public AuthAdminController(AuthAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/users")
    public ApiResult<List<UserView>> listUsers() {
        return ApiResult.success(administrationService.listUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResult<UserView>> createUser(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> administrationService.createUser(
                actor, request.username(), request.displayName(), request.temporaryPassword(), request.roles(),
                AuthRequestTrace.resolve(servletRequest)
        ));
    }

    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<ApiResult<UserView>> changeEnabled(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @RequestBody EnabledRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> administrationService.changeEnabled(
                actor,
                id,
                request.enabled(),
                AuthRequestTrace.resolve(servletRequest)
        ));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<ApiResult<UserView>> assignRoles(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody RolesRequest request,
            HttpServletRequest servletRequest
    ) {
        return execute(() -> administrationService.assignRoles(
                actor,
                id,
                request.roles(),
                AuthRequestTrace.resolve(servletRequest)
        ));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResult<Void>> resetPassword(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            administrationService.resetPassword(
                    actor,
                    id,
                    request.temporaryPassword(),
                    AuthRequestTrace.resolve(servletRequest)
            );
            return ResponseEntity.ok(ApiResult.success(null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    @GetMapping("/roles")
    public ApiResult<List<RoleView>> listRoles() {
        return ApiResult.success(administrationService.listRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResult<RoleView>> createRole(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody CreateRoleRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeRole(() -> administrationService.createRole(
                actor,
                request.roleKey(),
                request.displayName(),
                request.permissions(),
                AuthRequestTrace.resolve(servletRequest)
        ));
    }

    @PutMapping("/roles/{id}/permissions")
    public ResponseEntity<ApiResult<RoleView>> updatePermissions(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody PermissionsRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeRole(() -> administrationService.updateRolePermissions(
                actor,
                id,
                request.permissions(),
                AuthRequestTrace.resolve(servletRequest)
        ));
    }

    private ResponseEntity<ApiResult<UserView>> execute(UserOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        }
    }

    private ResponseEntity<ApiResult<RoleView>> executeRole(RoleOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        }
    }

    public record CreateUserRequest(
            @NotBlank String username,
            String displayName,
            @NotBlank String temporaryPassword,
            @NotEmpty Set<String> roles
    ) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record RolesRequest(@NotEmpty Set<String> roles) {
    }

    public record ResetPasswordRequest(@NotBlank String temporaryPassword) {
    }

    public record CreateRoleRequest(
            @NotBlank String roleKey,
            String displayName,
            @NotEmpty Set<PermissionKey> permissions
    ) {
    }

    public record PermissionsRequest(@NotEmpty Set<PermissionKey> permissions) {
    }

    @FunctionalInterface
    private interface UserOperation {
        UserView run();
    }

    @FunctionalInterface
    private interface RoleOperation {
        RoleView run();
    }
}
