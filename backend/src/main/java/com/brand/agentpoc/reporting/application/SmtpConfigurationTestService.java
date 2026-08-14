package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryRequest;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.tenant.application.TenantMemberDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class SmtpConfigurationTestService {

    private final ReportDeliveryPort deliveryPort;
    private final TenantMemberDirectory memberDirectory;
    private final AuthAuditService auditService;
    private final Clock clock;

    public SmtpConfigurationTestService(
            ReportDeliveryPort deliveryPort,
            TenantMemberDirectory memberDirectory,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.deliveryPort = deliveryPort;
        this.memberDirectory = memberDirectory;
        this.auditService = auditService;
        this.clock = clock;
    }

    public SmtpTestView send(AuthPrincipal actor, String traceId) {
        if (actor == null || !actor.enabled() || !actor.hasTenantContext()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        String email = memberDirectory.requireEmail(actor.tenantId(), actor.userId());
        Instant now = clock.instant();
        String deliveryKey = "smtp-test:" + actor.tenantId() + ":" + actor.userId() + ":" + UUID.randomUUID();
        DeliveryResult result;
        try {
            result = deliveryPort.deliver(new DeliveryRequest(
                    actor.tenantId(), email, "[Dealer AI] SMTP configuration test",
                    "This is a test email sent at " + now + ".\n\n"
                            + "Your tenant SMTP configuration can submit plain-text report notifications.",
                    deliveryKey));
        } catch (RuntimeException exception) {
            result = DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
        }
        if (result == null || result.outcome() == null) {
            result = DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
        }
        boolean accepted = result.outcome() == ReportDeliveryPort.Outcome.SUCCEEDED;
        String code = accepted ? "SMTP_ACCEPTED" : safeCode(result.errorCode());
        auditService.record(actor.tenantId(), actor.userId(), "SMTP_CONFIG_TEST", "TENANT_SMTP_CONFIG",
                String.valueOf(actor.tenantId()), accepted ? "SUCCESS" : "FAILURE", traceId, code);
        return new SmtpTestView(accepted, code, now);
    }

    private String safeCode(String code) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return "SMTP_TEST_FAILED";
        }
        return code;
    }

    public record SmtpTestView(boolean accepted, String code, Instant testedAt) {
    }
}
