package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.dto.response.DashboardSummary;
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

@Service
public class ReportService {

    public static final String PROMPT_VERSION = "reporting-v1";
    private static final int MAX_TOPIC_LENGTH = 500;

    private final DashboardService dashboardService;
    private final ImportBatchService importBatchService;
    private final ReportDraftStore draftStore;
    private final Clock clock;
    private final AppProperties appProperties;
    private final ReportMarkdownRenderer renderer;

    @Autowired
    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            AppProperties appProperties
    ) {
        this(dashboardService, importBatchService, draftStore, Clock.systemUTC(), appProperties,
                new ReportMarkdownRenderer());
    }

    public ReportService(
            DashboardService dashboardService,
            ImportBatchService importBatchService,
            ReportDraftStore draftStore,
            Clock clock,
            AppProperties appProperties,
            ReportMarkdownRenderer renderer
    ) {
        this.dashboardService = dashboardService;
        this.importBatchService = importBatchService;
        this.draftStore = draftStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.appProperties = appProperties == null ? new AppProperties() : appProperties;
        this.renderer = renderer == null ? new ReportMarkdownRenderer() : renderer;
    }

    public ReportDraft generate(ReportGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("report request is required.");
        }
        ReportType reportType = ReportType.parse(request.reportType());
        String language = validateLanguage(request.language());
        String topic = normalizeTopic(request.topic(), reportType);
        ReportScope scope = new ReportScope(request.scopeType(), request.scopeId());
        DashboardSummary summary = dashboardService.getSummary();
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
                PROMPT_VERSION
        );
        return draftStore.save(draft);
    }

    public ReportDraft require(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("report id is required.");
        }
        return draftStore.findById(id.trim())
                .orElseThrow(() -> new java.util.NoSuchElementException("Report draft was not found."));
    }

    public List<ReportDraft> list() {
        return draftStore.findAll().stream()
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
