package com.brand.agentpoc.organization.controller;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.organization.application.OrganizationAdministrationService;
import com.brand.agentpoc.organization.application.OrganizationAdministrationService.DealerMappingView;
import com.brand.agentpoc.organization.application.OrganizationAdministrationService.GrantInput;
import com.brand.agentpoc.organization.application.OrganizationAdministrationService.GrantView;
import com.brand.agentpoc.organization.application.OrganizationAdministrationService.NodeView;
import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations")
public class OrganizationAdminController {

    private static final int MAX_TRACE_ID_LENGTH = 128;

    private final OrganizationAdministrationService administrationService;

    public OrganizationAdminController(OrganizationAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/nodes")
    public ApiResult<List<NodeView>> listNodes() {
        return ApiResult.success(administrationService.listNodes());
    }

    @PostMapping("/nodes")
    public ResponseEntity<ApiResult<NodeView>> createNode(
            @AuthenticationPrincipal AuthPrincipal actor,
            @Valid @RequestBody NodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeNode(() -> administrationService.createNode(
                actor,
                request.nodeKey(),
                request.displayName(),
                request.nodeType(),
                request.parentId(),
                Boolean.TRUE.equals(request.enabled()),
                traceId(servletRequest)
        ));
    }

    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResult<NodeView>> updateNode(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long nodeId,
            @Valid @RequestBody NodeUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeNode(() -> administrationService.updateNode(
                actor,
                nodeId,
                request.displayName(),
                request.nodeType(),
                request.parentId(),
                Boolean.TRUE.equals(request.enabled()),
                request.version(),
                traceId(servletRequest)
        ));
    }

    @GetMapping("/dealer-mappings")
    public ApiResult<List<DealerMappingView>> listDealerMappings() {
        return ApiResult.success(administrationService.listDealerMappings());
    }

    @PutMapping("/dealer-mappings/{dealerCode}")
    public ResponseEntity<ApiResult<DealerMappingView>> mapDealer(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable String dealerCode,
            @Valid @RequestBody DealerMappingRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeMapping(() -> administrationService.mapDealer(
                actor,
                dealerCode,
                request.organizationNodeId(),
                traceId(servletRequest)
        ));
    }

    @PutMapping("/user-grants/{userId}")
    public ResponseEntity<ApiResult<List<GrantView>>> replaceUserGrants(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long userId,
            @Valid @RequestBody GrantsRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeGrants(() -> administrationService.replaceUserGrants(
                actor,
                userId,
                grantInputs(request.grants()),
                traceId(servletRequest)
        ));
    }

    @GetMapping("/user-grants/{userId}")
    public ResponseEntity<ApiResult<List<GrantView>>> listUserGrants(@PathVariable Long userId) {
        return executeGrants(() -> administrationService.listUserGrants(userId));
    }

    @PutMapping("/role-grants/{roleId}")
    public ResponseEntity<ApiResult<List<GrantView>>> replaceRoleGrants(
            @AuthenticationPrincipal AuthPrincipal actor,
            @PathVariable Long roleId,
            @Valid @RequestBody GrantsRequest request,
            HttpServletRequest servletRequest
    ) {
        return executeGrants(() -> administrationService.replaceRoleGrants(
                actor,
                roleId,
                grantInputs(request.grants()),
                traceId(servletRequest)
        ));
    }

    @GetMapping("/role-grants/{roleId}")
    public ResponseEntity<ApiResult<List<GrantView>>> listRoleGrants(@PathVariable Long roleId) {
        return executeGrants(() -> administrationService.listRoleGrants(roleId));
    }

    private String traceId(HttpServletRequest request) {
        String provided = request.getHeader("X-Request-ID");
        if (provided == null || provided.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = provided.trim();
        return normalized.length() <= MAX_TRACE_ID_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TRACE_ID_LENGTH);
    }

    private Set<GrantInput> grantInputs(Set<GrantRequest> grants) {
        return grants.stream()
                .map(grant -> new GrantInput(
                        grant.organizationNodeId(),
                        Boolean.TRUE.equals(grant.includeDescendants())
                ))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ResponseEntity<ApiResult<NodeView>> executeNode(NodeOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return ResponseEntity.status(409).body(ApiResult.error(409, "The resource changed since it was loaded."));
        }
    }

    private ResponseEntity<ApiResult<DealerMappingView>> executeMapping(MappingOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    private ResponseEntity<ApiResult<List<GrantView>>> executeGrants(GrantOperation operation) {
        try {
            return ResponseEntity.ok(ApiResult.success(operation.run()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
        }
    }

    public record NodeRequest(
            @NotBlank String nodeKey,
            @NotBlank String displayName,
            @NotNull OrganizationNodeType nodeType,
            Long parentId,
            @NotNull Boolean enabled
    ) {
    }

    public record NodeUpdateRequest(
            @NotBlank String displayName,
            @NotNull OrganizationNodeType nodeType,
            Long parentId,
            @NotNull Boolean enabled,
            Long version
    ) {
    }

    public record DealerMappingRequest(@NotNull Long organizationNodeId) {
    }

    public record GrantsRequest(@NotNull Set<@Valid GrantRequest> grants) {
    }

    public record GrantRequest(
            @NotNull Long organizationNodeId,
            @NotNull Boolean includeDescendants
    ) {
    }

    @FunctionalInterface
    private interface NodeOperation {
        NodeView run();
    }

    @FunctionalInterface
    private interface MappingOperation {
        DealerMappingView run();
    }

    @FunctionalInterface
    private interface GrantOperation {
        List<GrantView> run();
    }
}
