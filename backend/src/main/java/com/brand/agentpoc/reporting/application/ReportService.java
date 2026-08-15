package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import com.brand.agentpoc.service.DashboardService;
import com.brand.agentpoc.service.ImportBatchService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportService {

    public static final String PROMPT_VERSION = "reporting-v1";
    private static final int MAX_TOPIC_LENGTH = 500;
    private static final ReportCollaborationInitializer NOOP_COLLABORATION_INITIALIZER = draft -> { };

    private final DashboardService dashboardService;
    private final ImportBatchService importBatchService;
    private final ReportDraftStore draftStore;
    private final Clock clock;
    private final AppProperties appProperties;
    private final ReportMarkdownRenderer renderer;
    private final ReportCollaborationInitializer collaborationInitializer;

    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            AppProperties appProperties
    ) {
        this(dashboardService, importBatchService, draftStore, Clock.systemUTC(), appProperties,
                new ReportMarkdownRenderer(), NOOP_COLLABORATION_INITIALIZER);
    }

    @Autowired
    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            AppProperties appProperties,
            ReportCollaborationInitializer collaborationInitializer
    ) {
        this(dashboardService, importBatchService, draftStore, Clock.systemUTC(), appProperties,
                new ReportMarkdownRenderer(), collaborationInitializer);
    }

    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            Clock clock,
            AppProperties appProperties,
            ReportMarkdownRenderer renderer
    ) {
        this(dashboardService, importBatchService, draftStore, clock, appProperties, renderer,
                NOOP_COLLABORATION_INITIALIZER);
    }

    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            Clock clock,
            AppProperties appProperties,
            ReportMarkdownRenderer renderer,
            ReportCollaborationInitializer collaborationInitializer
    ) {
        this.dashboardService = dashboardService;
        this.importBatchService = importBatchService;
        this.draftStore = draftStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.appProperties = appProperties == null ? new AppProperties() : appProperties;
        this.renderer = renderer == null ? new ReportMarkdownRenderer() : renderer;
        this.collaborationInitializer = collaborationInitializer == null
                ? NOOP_COLLABORATION_INITIALIZER : collaborationInitializer;
    }

    public ReportDraft generate(ReportGenerationRequest request) {
        return generate(request, OrganizationDataScope.unrestrictedScope());
    }

    public ReportDraft generate(ReportGenerationRequest request, OrganizationDataScope dataScope) {
        if (request == null) {
            throw new IllegalArgumentException("report request is required.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireDataAccess();
        ReportType reportType = ReportType.parse(request.reportType());
        String language = validateLanguage(request.language());
        String topic = normalizeTopic(request.topic(), reportType);
        ReportScope requestedScope = new ReportScope(request.scopeType(), request.scopeId());
        ReportScope scope = requiredScope.unrestricted()
                ? requestedScope
                : ReportScope.organization(requiredScope.grantNodeIds());
        DashboardSummary summary = requiredScope.unrestricted()
                ? dashboardService.getSummary()
                : dashboardService.getSummary(requiredScope);
        String batchId = summary.dataStatus() != null && summary.dataStatus().batch() != null
                ? summary.dataStatus().batch().id()
                : importBatchService.activeBatchId();
        String model = appProperties.getModel().getName();
        if (model == null || model.isBlank()) {
            model = "deterministic";
        }
        Instant generatedAt = clock.instant();
        ReportDraft draft = new ReportDraft(
                UUID.randomUUID().toString(),
                reportType,
                rendererTitle(reportType, language),
                language,
                renderer.render(reportType, language, summary, topic, batchId),
                generatedAt,
                batchId,
                scope,
                model,
                PROMPT_VERSION,
                requiredScope.tenantId()
        );
        ReportDraft saved = draftStore.save(draft);
        collaborationInitializer.initialize(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public ReportDraft require(String id) {
        return require(id, OrganizationDataScope.unrestrictedScope());
    }

    @Transactional(readOnly = true)
    public ReportDraft require(String id, OrganizationDataScope dataScope) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("report id is required.");
        }
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireDataAccess();
        ReportDraft draft = draftStore.findByTenantIdAndId(requiredScope.tenantId(), id.trim())
                .orElseThrow(() -> new java.util.NoSuchElementException("Report draft was not found."));
        if (!ReportAccessPolicy.canRead(draft, requiredScope)) {
            throw new AccessDeniedException("Report draft is outside the active organization scope.");
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public List<ReportDraft> list() {
        return list(OrganizationDataScope.unrestrictedScope());
    }

    @Transactional(readOnly = true)
    public List<ReportDraft> list(OrganizationDataScope dataScope) {
        OrganizationDataScope requiredScope = dataScope == null ? OrganizationDataScope.empty() : dataScope;
        requiredScope.requireDataAccess();
        return draftStore.findAllByTenantId(requiredScope.tenantId()).stream()
                .filter(draft -> ReportAccessPolicy.canRead(draft, requiredScope))
                .sorted(Comparator.comparing(ReportDraft::generatedAt).reversed())
                .collect(Collectors.toUnmodifiableList());
    }

    private String validateLanguage(String language) {
        if ("zh".equalsIgnoreCase(language)) {
            return "zh";
        }
        if ("en".equalsIgnoreCase(language)) {
            return "en";
        }
        throw new IllegalArgumentException("language must be zh or en.");
    }

    private String normalizeTopic(String topic, ReportType reportType) {
        String normalized = topic == null ? "" : topic.trim();
        if (reportType == ReportType.TOPIC && normalized.isBlank()) {
            throw new IllegalArgumentException("topic is required for a topic report.");
        }
        if (normalized.length() > MAX_TOPIC_LENGTH) {
            throw new IllegalArgumentException("topic exceeds the allowed length.");
        }
        return normalized;
    }

    private String rendererTitle(ReportType reportType, String language) {
        return renderer.renderTitle(reportType, language);
    }
}
