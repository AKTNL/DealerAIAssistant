package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.entity.Task;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import com.brand.agentpoc.service.importing.ImportQualityTracker;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);
    private static final String TARGET_SHEET = "AE Target Data";
    private static final String OPPORTUNITY_SHEET = "Opportunity";
    private static final String CAMPAIGN_SHEET = "Campaign";
    private static final String LEAD_SHEET = "Lead";
    private static final String TASK_SHEET = "Task";
    private static final String DEALER_SHEET = "Dealer";
    private static final Set<String> NULL_MARKERS = Set.of("null", "n/a", "na", "-", "--");

    private static final DateTimeFormatter[] DATE_PATTERNS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/M/dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy.M.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-d"),
            DateTimeFormatter.BASIC_ISO_DATE
    };

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;
    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;
    private final ImportQualityService importQualityService;
    private final ImportBatchService importBatchService;
    private final DataFormatter dataFormatter = new DataFormatter();

    @Autowired
    public ExcelImportService(
            AppProperties appProperties,
            ResourceLoader resourceLoader,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository,
            ImportQualityService importQualityService,
            ImportBatchService importBatchService
    ) {
        this.appProperties = appProperties;
        this.resourceLoader = resourceLoader;
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
        this.importQualityService = importQualityService;
        this.importBatchService = importBatchService;
    }

    ExcelImportService(
            AppProperties appProperties,
            ResourceLoader resourceLoader,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository
    ) {
        this(
                appProperties,
                resourceLoader,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                new ImportQualityService(),
                new ImportBatchService()
        );
    }

    ExcelImportService(
            AppProperties appProperties,
            ResourceLoader resourceLoader,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository,
            ImportQualityService importQualityService
    ) {
        this(
                appProperties,
                resourceLoader,
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository,
                importQualityService,
                new ImportBatchService()
        );
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        importConfiguredWorkbook(com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID);
    }

    @Transactional
    public void importConfiguredWorkbook(Long tenantId) {
        requireTenantId(tenantId);
        if (hasExistingData(tenantId)) {
            log.info("Sample data already initialized, skipping startup import.");
            publishRepositoryStatus(tenantId, "existing-database", false, "Existing database data is active.");
            return;
        }

        ImportQualityTracker tracker = new ImportQualityTracker();
        Resource resource = resolveConfiguredResource(appProperties.getExcel().getPath());
        String batchId = importBatchService.newBatchId("startup");
        try {
            if (resource == null || !resource.exists()) {
                throw new IllegalStateException("Configured workbook was not found: " + appProperties.getExcel().getPath());
            }

            ParsedWorkbook parsedWorkbook = importWorkbook(resource, tracker, batchId, tenantId);
            persistParsedWorkbook(parsedWorkbook, tenantId);
            importBatchService.activateTenantBatch(
                    tenantId,
                    batchId,
                    "configured-workbook",
                    false,
                    "Configured workbook imported successfully."
            );
            importQualityService.publish(tenantId, tracker.build(
                    "configured-workbook",
                    false,
                    "Configured workbook imported successfully.",
                    importBatchService.activeStatusBatch(tenantId)
            ));
            logImportCompletion(tenantId, "configured-workbook");
        } catch (Exception exception) {
            handleImportFailure(tenantId, tracker, exception);
        }
    }

    private boolean hasExistingData(Long tenantId) {
        return !dealerRepository.findByTenantId(tenantId).isEmpty()
                || !opportunityRepository.findByTenantId(tenantId).isEmpty()
                || !campaignRepository.findByTenantId(tenantId).isEmpty()
                || !taskRepository.findByTenantId(tenantId).isEmpty()
                || !targetRepository.findByTenantId(tenantId).isEmpty()
                || !leadRepository.findByTenantId(tenantId).isEmpty();
    }

    private Resource resolveConfiguredResource(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }

        String trimmedPath = configuredPath.trim();
        if (trimmedPath.startsWith("classpath:") || trimmedPath.startsWith("file:")) {
            return resourceLoader.getResource(trimmedPath);
        }

        Path filePath = Path.of(trimmedPath);
        if (Files.exists(filePath)) {
            return new FileSystemResource(filePath);
        }

        return resourceLoader.getResource(trimmedPath);
    }

    private ParsedWorkbook importWorkbook(
            Resource resource,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) throws Exception {
        log.info("Attempting workbook import from {}", resource);
        try (InputStream inputStream = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            validateRequiredSheets(workbook, tracker);
            ParsedWorkbook parsedWorkbook = parseWorkbook(workbook, tracker, batchId, tenantId);
            if (parsedWorkbook.isEmpty()) {
                throw new IllegalStateException("Workbook import produced no usable rows.");
            }
            return parsedWorkbook;
        }
    }

    private ParsedWorkbook parseWorkbook(
            Workbook workbook,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        // Parse AE Target Data first to extract dealer group name lookup
        Map<String, String> dealerGroupByCode = new LinkedHashMap<>();
        List<Target> targets = parseTargetSheet(
                findSheet(workbook, TARGET_SHEET, "Target", "Targets"), dealerGroupByCode, tracker, batchId, tenantId);

        // Parse sheets that have direct dealer info
        List<Opportunity> opportunities = parseOpportunitySheet(
                findSheet(workbook, OPPORTUNITY_SHEET, "Opportunities"), dealerGroupByCode, tracker, batchId, tenantId);
        List<Campaign> campaigns = parseCampaignSheet(
                findSheet(workbook, CAMPAIGN_SHEET, "Campaigns"), dealerGroupByCode, tracker, batchId, tenantId);
        List<Lead> leads = parseLeadSheet(
                findSheet(workbook, LEAD_SHEET, "Leads"), dealerGroupByCode, tracker, batchId, tenantId);

        // Task sheet has no direct dealer info — join via Opportunity
        Map<String, String[]> oppDealerInfo = new LinkedHashMap<>();
        for (Opportunity opp : opportunities) {
            oppDealerInfo.put(opp.getOpportunityId(),
                    new String[]{opp.getDealerCode(), opp.getDealerName(), opp.getCity(), opp.getDealerGroupName()});
        }
        List<Task> tasks = parseTaskSheet(
                findSheet(workbook, TASK_SHEET, "Tasks"), oppDealerInfo, dealerGroupByCode, tracker, batchId, tenantId);

        List<Dealer> dealers = deriveDealers(opportunities, campaigns, tasks, targets, leads, batchId, tenantId);
        tracker.imported(DEALER_SHEET, dealers.size());
        return new ParsedWorkbook(dealers, opportunities, campaigns, tasks, targets, leads);
    }

    private void validateRequiredSheets(Workbook workbook, ImportQualityTracker tracker) {
        Map<String, String[]> requiredSheets = Map.of(
                TARGET_SHEET, new String[]{TARGET_SHEET, "Target", "Targets"},
                OPPORTUNITY_SHEET, new String[]{OPPORTUNITY_SHEET, "Opportunities"},
                CAMPAIGN_SHEET, new String[]{CAMPAIGN_SHEET, "Campaigns"},
                LEAD_SHEET, new String[]{LEAD_SHEET, "Leads"},
                TASK_SHEET, new String[]{TASK_SHEET, "Tasks"}
        );
        List<String> missing = requiredSheets.entrySet().stream()
                .filter(entry -> findSheet(workbook, entry.getValue()) == null)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            missing.forEach(sheet -> tracker.issue(sheet, "missing_required_sheet"));
            throw new IllegalStateException("Workbook is missing required sheets: " + String.join(", ", missing));
        }
    }

    private void handleImportFailure(Long tenantId, ImportQualityTracker tracker, Exception exception) {
        tracker.issue("Workbook", "import_failed");
        if (!appProperties.getExcel().isFallbackEnabled()) {
            importQualityService.publish(tenantId, tracker.build(
                    "import-failed",
                    false,
                    exception.getMessage(),
                    importBatchService.activeStatusBatch(tenantId)
            ));
            throw new IllegalStateException("Workbook import failed in strict mode.", exception);
        }

        log.error("Workbook import failed. Seeding built-in sample data because fallback is enabled.", exception);
        String batchId = importBatchService.newBatchId("fallback");
        seedFallbackData(batchId, tenantId);
        importBatchService.activateTenantBatch(
                tenantId,
                batchId,
                "built-in-sample",
                true,
                "Configured workbook could not be used; built-in sample data is active."
        );
        recordRepositoryCounts(tenantId, tracker);
        importQualityService.publish(tenantId, tracker.build(
                "built-in-sample",
                true,
                "Configured workbook could not be used; built-in sample data is active.",
                importBatchService.activeStatusBatch(tenantId)
        ));
        logImportCompletion(tenantId, "built-in-sample");
    }

    private void publishRepositoryStatus(Long tenantId, String source, boolean fallbackActive, String message) {
        ImportQualityTracker tracker = new ImportQualityTracker();
        recordRepositoryCounts(tenantId, tracker);
        importQualityService.publish(tenantId, tracker.build(
                source, fallbackActive, message, importBatchService.activeStatusBatch(tenantId)));
    }

    private void recordRepositoryCounts(Long tenantId, ImportQualityTracker tracker) {
        tracker.imported(DEALER_SHEET, importBatchService.filterActive(dealerRepository.findByTenantId(tenantId), tenantId).size());
        tracker.imported(OPPORTUNITY_SHEET, importBatchService.filterActive(
                opportunityRepository.findByTenantId(tenantId), tenantId).size());
        tracker.imported(CAMPAIGN_SHEET, importBatchService.filterActive(
                campaignRepository.findByTenantId(tenantId), tenantId).size());
        tracker.imported(TASK_SHEET, importBatchService.filterActive(taskRepository.findByTenantId(tenantId), tenantId).size());
        tracker.imported(TARGET_SHEET, importBatchService.filterActive(targetRepository.findByTenantId(tenantId), tenantId).size());
        tracker.imported(LEAD_SHEET, importBatchService.filterActive(leadRepository.findByTenantId(tenantId), tenantId).size());
    }

    private void logImportCompletion(Long tenantId, String source) {
        log.info(
                "Data initialization completed: source={}, dealers={}, opportunities={}, campaigns={}, tasks={}, targets={}, leads={}",
                source,
                importBatchService.filterActive(dealerRepository.findByTenantId(tenantId), tenantId).size(),
                importBatchService.filterActive(opportunityRepository.findByTenantId(tenantId), tenantId).size(),
                importBatchService.filterActive(campaignRepository.findByTenantId(tenantId), tenantId).size(),
                importBatchService.filterActive(taskRepository.findByTenantId(tenantId), tenantId).size(),
                importBatchService.filterActive(targetRepository.findByTenantId(tenantId), tenantId).size(),
                importBatchService.filterActive(leadRepository.findByTenantId(tenantId), tenantId).size()
        );
    }

    private void persistParsedWorkbook(ParsedWorkbook parsedWorkbook, Long tenantId) {
        parsedWorkbook.requireTenant(tenantId);
        dealerRepository.saveAll(parsedWorkbook.dealers());
        opportunityRepository.saveAll(parsedWorkbook.opportunities());
        campaignRepository.saveAll(parsedWorkbook.campaigns());
        taskRepository.saveAll(parsedWorkbook.tasks());
        targetRepository.saveAll(parsedWorkbook.targets());
        leadRepository.saveAll(parsedWorkbook.leads());
    }

    private Long requireTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        return tenantId;
    }

    private Sheet findSheet(Workbook workbook, String... candidateNames) {
        Set<String> exactCandidates = Stream.of(candidateNames)
                .map(this::normalizeHeader)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        for (Sheet sheet : workbook) {
            if (exactCandidates.contains(normalizeHeader(sheet.getSheetName()))) {
                return sheet;
            }
        }

        for (Sheet sheet : workbook) {
            String normalizedSheetName = normalizeHeader(sheet.getSheetName());
            for (String candidateName : exactCandidates) {
                if (normalizedSheetName != null && normalizedSheetName.contains(candidateName)) {
                    return sheet;
                }
            }
        }

        return null;
    }

    private List<Opportunity> parseOpportunitySheet(
            Sheet sheet,
            Map<String, String> dealerGroupByCode,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Opportunity> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            tracker.processed(OPPORTUNITY_SHEET);

            String opportunityId = getString(row, headerInfo.headers(), "opportunityid", "id", "商机id", "商机编号");
            String dealerCode = getString(row, headerInfo.headers(), "dealercode", "经销商代码", "门店代码");
            String dealerName = getString(row, headerInfo.headers(), "dealername", "经销商名称", "门店名称",
                    "salesretailerr.name", "retailerr.name");
            String productModel = getString(row, headerInfo.headers(), "productmodel", "车型", "产品型号", "model");
            String purchaseHorizon = getString(row, headerInfo.headers(), "purchasehorizon", "purchasehorizonc",
                    "purchasehorizon__c", "purchase_horizon__c", "购买周期");
            String stageName = getString(row, headerInfo.headers(), "stagename", "阶段", "阶段名称", "商机阶段");
            String leadSource = getString(row, headerInfo.headers(), "leadsource", "线索来源", "来源");
            LocalDate createdDate = getDate("createdDate", row, headerInfo.headers(), "createddate", "创建日期", "创建时间", "日期");
            LocalDate expectedCloseDate = getDate("expectedCloseDate", row, headerInfo.headers(), "expectedclosedate",
                    "预计成交日期", "预计关闭日期", "expectedclosedate");
            Integer probability = getInteger("probability", row, headerInfo.headers(), "probability", "成交概率", "赢单概率", "概率");

            if (hasBlank(opportunityId, dealerCode, dealerName, stageName)
                    || createdDate == null
                    || probability == null) {
                tracker.skipped(OPPORTUNITY_SHEET, "missing_required_field");
                log.debug("Skipping opportunity row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (!seenIds.add(opportunityId)) {
                tracker.skipped(OPPORTUNITY_SHEET, "duplicate_opportunity_id");
                continue;
            }
            if (probability < 0 || probability > 100
                    || (expectedCloseDate != null && expectedCloseDate.isBefore(createdDate))) {
                tracker.skipped(OPPORTUNITY_SHEET, "invalid_probability_or_date_range");
                continue;
            }

            if (expectedCloseDate == null) {
                tracker.issue(OPPORTUNITY_SHEET, "missing_expected_close_date");
            }

            if (productModel == null) {
                tracker.normalized(OPPORTUNITY_SHEET, "unknown_product_model");
                productModel = "未知";
            }
            if (purchaseHorizon == null) {
                tracker.normalized(OPPORTUNITY_SHEET, "unknown_purchase_horizon");
                purchaseHorizon = "未知";
            }
            if (leadSource == null) {
                tracker.normalized(OPPORTUNITY_SHEET, "unknown_lead_source");
                leadSource = "未知";
            }
            String city = deriveCity(dealerName);
            String dealerGroupName = lookupDealerGroup(dealerCode, dealerGroupByCode);

            items.add(new Opportunity(
                    opportunityId,
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName,
                    productModel,
                    purchaseHorizon,
                    stageName,
                    leadSource,
                    createdDate,
                    expectedCloseDate,
                    probability,
                    batchId,
                    tenantId
            ));
            tracker.imported(OPPORTUNITY_SHEET);
        }

        return items;
    }

    private List<Campaign> parseCampaignSheet(
            Sheet sheet,
            Map<String, String> dealerGroupByCode,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Campaign> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            tracker.processed(CAMPAIGN_SHEET);

            String campaignId = getString(row, headerInfo.headers(), "campaignid", "campaignidc", "campaignid__c",
                    "id", "活动id", "活动编号");
            String campaignName = getString(row, headerInfo.headers(), "name", "活动名称", "campaignname");
            String dealerCode = getString(row, headerInfo.headers(), "dealercode", "经销商代码", "门店代码",
                    "retailerc");
            String dealerName = getString(row, headerInfo.headers(), "dealername", "经销商名称", "门店名称",
                    "salesretailerr.name", "retailerr.name");
            String productModel = getString(row, headerInfo.headers(), "productmodel", "车型", "产品型号", "model",
                    "productmodelc", "product_model__c");
            String eventType = getString(row, headerInfo.headers(), "type", "活动大类", "活动类型");
            String campaignType = getString(row, headerInfo.headers(), "campaigntypec", "campaigntype__c",
                    "campaigntype", "活动子类型");
            LocalDate createdDate = getDate("createdDate", row, headerInfo.headers(), "createddate", "创建日期", "创建时间", "日期");
            Integer targetOpportunityAmount = getInteger("targetOpportunityAmount", row, headerInfo.headers(),
                    "targetopportunityamountc", "target_opportunity_amount__c", "目标商机数", "目标商机");
            Integer actualOpportunityCount = getInteger("actualOpportunityCount", row, headerInfo.headers(),
                    "actualopportunitycount", "实际商机数", "商机数", "实际新增商机数", "numberofopportunities");
            Integer targetOrderAmount = getInteger("targetOrderAmount", row, headerInfo.headers(),
                    "targetorderamountc", "target_order_amount__c", "目标订单数", "目标订单");
            Integer wonOpportunityCount = getInteger("wonOpportunityCount", row, headerInfo.headers(),
                    "numberofwonopportunities", "wonopportunitycount", "赢单数", "订单数");
            Integer leadCount = getInteger("leadCount", row, headerInfo.headers(),
                    "numberofleads", "leadcount", "线索数");
            Integer totalNewCustomerTarget = getInteger("totalNewCustomerTarget", row, headerInfo.headers(),
                    "targetopportunityamountc", "target_opportunity_amount__c", "totalnewcustomertarget",
                    "新增客户目标", "新客户目标", "新客目标", "newcustomercountc");

            if (campaignName == null) {
                campaignName = campaignId;
                tracker.normalized(CAMPAIGN_SHEET, "campaign_name_from_id");
                log.debug("[Import-Normalization] Row {}: campaignName is blank, defaulting to campaignId", rowIndex + 1);
            }
            if (eventType == null) {
                eventType = "未知";
                tracker.normalized(CAMPAIGN_SHEET, "unknown_event_type");
                log.debug("[Import-Normalization] Row {}: eventType is blank, defaulting to unknown", rowIndex + 1);
            }
            if (campaignType == null) {
                campaignType = "未知";
                tracker.normalized(CAMPAIGN_SHEET, "unknown_campaign_type");
                log.debug("[Import-Normalization] Row {}: campaignType is blank, defaulting to unknown", rowIndex + 1);
            }
            if (dealerCode == null) {
                dealerCode = "未分配";
                tracker.normalized(CAMPAIGN_SHEET, "unassigned_dealer_code");
                log.debug("[Import-Normalization] Row {}: dealerCode is blank, defaulting to '未分配'", rowIndex + 1);
            }
            if (dealerName == null) {
                dealerName = "未分配";
                tracker.normalized(CAMPAIGN_SHEET, "unassigned_dealer_name");
                log.debug("[Import-Normalization] Row {}: dealerName is blank, defaulting to '未分配'", rowIndex + 1);
            }
            if (productModel == null) {
                productModel = "未知";
                tracker.normalized(CAMPAIGN_SHEET, "unknown_product_model");
                log.debug("[Import-Normalization] Row {}: productModel is blank, defaulting to '未知'", rowIndex + 1);
            }

            targetOpportunityAmount = nonNegativeOrNull(
                    CAMPAIGN_SHEET, "target_opportunity_amount", targetOpportunityAmount, tracker);
            actualOpportunityCount = nonNegativeOrNull(
                    CAMPAIGN_SHEET, "actual_opportunity_count", actualOpportunityCount, tracker);
            targetOrderAmount = nonNegativeOrNull(
                    CAMPAIGN_SHEET, "target_order_amount", targetOrderAmount, tracker);
            wonOpportunityCount = nonNegativeOrNull(
                    CAMPAIGN_SHEET, "won_opportunity_count", wonOpportunityCount, tracker);
            leadCount = nonNegativeOrNull(CAMPAIGN_SHEET, "lead_count", leadCount, tracker);
            totalNewCustomerTarget = nonNegativeOrNull(
                    CAMPAIGN_SHEET, "new_customer_target", totalNewCustomerTarget, tracker);

            recordMissingCampaignMetrics(
                    tracker,
                    targetOpportunityAmount,
                    actualOpportunityCount,
                    targetOrderAmount,
                    wonOpportunityCount,
                    leadCount,
                    totalNewCustomerTarget
            );

            if (hasBlank(campaignId) || createdDate == null) {
                tracker.skipped(CAMPAIGN_SHEET, "missing_required_field");
                log.debug("Skipping campaign row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (!seenIds.add(campaignId)) {
                tracker.skipped(CAMPAIGN_SHEET, "duplicate_campaign_id");
                continue;
            }
            String city = deriveCity(dealerName);
            String dealerGroupName = lookupDealerGroup(dealerCode, dealerGroupByCode);

            items.add(new Campaign(
                    campaignId,
                    campaignName,
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName,
                    productModel,
                    eventType,
                    campaignType,
                    createdDate,
                    targetOpportunityAmount,
                    actualOpportunityCount,
                    targetOrderAmount,
                    wonOpportunityCount,
                    leadCount,
                    totalNewCustomerTarget,
                    batchId,
                    tenantId
            ));
            tracker.imported(CAMPAIGN_SHEET);
        }

        return items;
    }

    private List<Task> parseTaskSheet(
            Sheet sheet,
            Map<String, String[]> oppDealerInfo,
            Map<String, String> dealerGroupByCode,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Task> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            tracker.processed(TASK_SHEET);

            String taskId = getString(row, headerInfo.headers(), "taskid", "id", "任务id", "任务编号");
            String opportunityId = getString(row, headerInfo.headers(), "opportunityid", "商机id", "商机编号");
            String subject = getString(row, headerInfo.headers(), "subject", "任务类型", "主题");
            String status = getString(row, headerInfo.headers(), "status", "任务状态", "状态");
            LocalDate createdDate = getDate("createdDate", row, headerInfo.headers(), "createddate", "创建日期", "创建时间", "日期");

            // Task sheet has no dealer columns — look up from parsed Opportunities
            String dealerCode = "";
            String dealerName = "";
            String city = "";
            String dealerGroupName = "";
            if (opportunityId != null && oppDealerInfo.containsKey(opportunityId)) {
                String[] info = oppDealerInfo.get(opportunityId);
                dealerCode = info[0];
                dealerName = info[1];
                city = info[2];
                dealerGroupName = info[3];
            } else {
                dealerGroupName = lookupDealerGroup(dealerCode, dealerGroupByCode);
            }

            if (hasBlank(taskId, opportunityId, status) || createdDate == null) {
                tracker.skipped(TASK_SHEET, "missing_required_field");
                log.debug("Skipping task row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (!seenIds.add(taskId)) {
                tracker.skipped(TASK_SHEET, "duplicate_task_id");
                continue;
            }
            if (subject == null) {
                tracker.normalized(TASK_SHEET, "unknown_subject");
                subject = "未知";
            }

            items.add(new Task(
                    taskId,
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName,
                    opportunityId,
                    subject,
                    status,
                    createdDate,
                    batchId,
                    tenantId
            ));
            tracker.imported(TASK_SHEET);
        }

        return items;
    }

    private List<Target> parseTargetSheet(
            Sheet sheet,
            Map<String, String> dealerGroupByCode,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Target> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            tracker.processed(TARGET_SHEET);

            String dealerCode = getString(row, headerInfo.headers(), "dealercode", "经销商代码", "门店代码");
            String dealerName = getString(row, headerInfo.headers(), "dealername", "经销商名称", "门店名称",
                    "salesretailerr.name", "retailerr.name");
            String city = deriveCity(dealerName);
            String dealerGroupName = getString(row, headerInfo.headers(), "dealergroupname", "集团名称",
                    "经销商集团名称", "dealergroupnamec");
            String productModel = getString(row, headerInfo.headers(), "productmodel", "车型", "产品型号", "model",
                    "productmodelc");
            Integer targetYear = getInteger("targetYear", row, headerInfo.headers(), "targetyear", "目标年份", "年份", "yearc");
            Integer targetMonth = getInteger("targetMonth", row, headerInfo.headers(), "targetmonth", "目标月份", "月份", "monthc");
            Integer asKTarget = getInteger("asKTarget", row, headerInfo.headers(), "asktarget", "目标值", "销量目标", "ask目标",
                    "aaktargetc");
            Integer opportunityWonCount = getInteger("opportunityWonCount", row, headerInfo.headers(),
                    "opportunitywoncount", "成交商机数", "已成交商机数", "赢单数", "opportunitywoncountc");
            Integer opportunityCreateCount = getInteger("opportunityCreateCount", row, headerInfo.headers(),
                    "opportunitycreatecount", "商机创建数", "商机创建数量",
                    "opportunitycreatecountc");
            if (hasBlank(dealerCode, dealerName, productModel)
                    || targetYear == null
                    || targetMonth == null
                    || opportunityCreateCount == null
                    || opportunityWonCount == null) {
                tracker.skipped(TARGET_SHEET, "missing_required_target_field");
                log.debug("Skipping target row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (targetMonth < 1 || targetMonth > 12
                    || (asKTarget != null && asKTarget < 0)
                    || opportunityWonCount < 0
                    || opportunityCreateCount < 0) {
                tracker.skipped(TARGET_SHEET, "invalid_target_value");
                continue;
            }
            if (asKTarget == null) {
                tracker.issue(TARGET_SHEET, "missing_ask_target");
            }
            String targetKey = String.join("|", dealerCode, productModel,
                    targetYear.toString(), targetMonth.toString());
            if (!seenKeys.add(targetKey)) {
                tracker.skipped(TARGET_SHEET, "duplicate_target_key");
                continue;
            }

            // Populate dealer group lookup for other sheets
            if (dealerGroupName != null && !dealerGroupName.isBlank()
                    && dealerCode != null && !dealerCode.isBlank()) {
                dealerGroupByCode.putIfAbsent(dealerCode, dealerGroupName);
            }

            items.add(new Target(
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName != null ? dealerGroupName : "",
                    productModel,
                    targetYear,
                    targetMonth,
                    asKTarget,
                    opportunityWonCount,
                    opportunityCreateCount,
                    batchId,
                    tenantId
            ));
            tracker.imported(TARGET_SHEET);
        }

        return items;
    }

    private List<Lead> parseLeadSheet(
            Sheet sheet,
            Map<String, String> dealerGroupByCode,
            ImportQualityTracker tracker,
            String batchId,
            Long tenantId
    ) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Lead> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            tracker.processed(LEAD_SHEET);

            String leadId = getString(row, headerInfo.headers(), "leadid", "id", "线索id", "线索编号");
            String dealerCode = getString(row, headerInfo.headers(), "dealercode", "经销商代码", "门店代码");
            String dealerName = getString(row, headerInfo.headers(), "dealername", "经销商名称", "门店名称",
                    "salesretailerr.name", "retailerr.name");
            String productModel = getString(row, headerInfo.headers(), "productmodel", "车型", "产品型号", "model");
            String leadSource = getString(row, headerInfo.headers(), "leadsource", "线索来源", "来源");
            String stageName = getString(row, headerInfo.headers(), "stagename", "阶段", "阶段名称", "线索阶段",
                    "status");
            LocalDate createdDate = getDate("createdDate", row, headerInfo.headers(), "createddate", "创建日期", "创建时间", "日期");
            Boolean converted = getBoolean(row, headerInfo.headers(), "isconverted", "converted", "是否转化", "已转化");

            if (hasBlank(leadId, stageName)
                    || converted == null) {
                tracker.skipped(LEAD_SHEET, "missing_required_field");
                log.debug("Skipping lead row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (!seenIds.add(leadId)) {
                tracker.skipped(LEAD_SHEET, "duplicate_lead_id");
                continue;
            }

            if (dealerCode == null) {
                tracker.normalized(LEAD_SHEET, "unassigned_dealer_code");
                dealerCode = "未分配";
            }
            if (dealerName == null) {
                tracker.normalized(LEAD_SHEET, "unassigned_dealer_name");
                dealerName = "未分配";
            }
            if (productModel == null) {
                tracker.normalized(LEAD_SHEET, "unknown_product_model");
                productModel = "未知";
            }
            if (leadSource == null) {
                tracker.normalized(LEAD_SHEET, "unknown_lead_source");
                leadSource = "未知";
            }
            if (createdDate == null) {
                tracker.issue(LEAD_SHEET, "missing_optional_created_date");
            }
            String city = deriveCity(dealerName);
            String dealerGroupName = lookupDealerGroup(dealerCode, dealerGroupByCode);

            items.add(new Lead(
                    leadId,
                    dealerCode,
                    dealerName,
                    city,
                    dealerGroupName,
                    leadSource,
                    stageName,
                    productModel,
                    createdDate,
                    converted,
                    batchId,
                    tenantId
            ));
            tracker.imported(LEAD_SHEET);
        }

        return items;
    }

    private HeaderInfo detectHeaderInfo(Sheet sheet) {
        int maxHeaderRow = Math.min(sheet.getLastRowNum(), 5);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxHeaderRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> headers = new LinkedHashMap<>();
            for (Cell cell : row) {
                String normalizedHeader = normalizeHeader(dataFormatter.formatCellValue(cell));
                if (normalizedHeader != null) {
                    headers.putIfAbsent(normalizedHeader, cell.getColumnIndex());
                }
            }

            if (headers.size() >= 3) {
                return new HeaderInfo(rowIndex, headers);
            }
        }
        return null;
    }

    private List<Dealer> deriveDealers(
            List<Opportunity> opportunities,
            List<Campaign> campaigns,
            List<Task> tasks,
            List<Target> targets,
            List<Lead> leads,
            String batchId,
            Long tenantId
    ) {
        Map<String, Dealer> dealers = new LinkedHashMap<>();

        opportunities.forEach(opportunity -> addDealer(dealers,
                opportunity.getDealerCode(), opportunity.getDealerName(), opportunity.getCity(),
                opportunity.getDealerGroupName(), batchId, tenantId));
        campaigns.forEach(campaign -> addDealer(dealers,
                campaign.getDealerCode(), campaign.getDealerName(), campaign.getCity(), campaign.getDealerGroupName(),
                batchId, tenantId));
        tasks.forEach(task -> addDealer(dealers,
                task.getDealerCode(), task.getDealerName(), task.getCity(), task.getDealerGroupName(), batchId, tenantId));
        targets.forEach(target -> addDealer(dealers,
                target.getDealerCode(), target.getDealerName(), target.getCity(), target.getDealerGroupName(), batchId, tenantId));
        leads.forEach(lead -> addDealer(dealers,
                lead.getDealerCode(), lead.getDealerName(), lead.getCity(), lead.getDealerGroupName(), batchId, tenantId));

        return new ArrayList<>(dealers.values());
    }

    private void addDealer(
            Map<String, Dealer> dealers,
            String dealerCode,
            String dealerName,
            String city,
            String dealerGroupName,
            String batchId,
            Long tenantId
    ) {
        if (hasBlank(dealerCode, dealerName, city)) {
            return;
        }
        dealers.putIfAbsent(dealerCode, new Dealer(dealerCode, dealerName, city,
                dealerGroupName != null ? dealerGroupName : "", batchId, tenantId));
    }

    private String getString(Row row, Map<String, Integer> headers, String... aliases) {
        Integer columnIndex = findColumnIndex(headers, aliases);
        if (columnIndex == null) {
            return null;
        }

        String value = dataFormatter.formatCellValue(row.getCell(columnIndex));
        if (value == null) {
            return null;
        }

        return normalizeCellText(value);
    }

    private Integer getInteger(String fieldName, Row row, Map<String, Integer> headers, String... aliases) {
        Integer columnIndex = findColumnIndex(headers, aliases);
        if (columnIndex == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }

        String text = normalizeCellText(dataFormatter.formatCellValue(cell));
        if (text == null) {
            return null;
        }

        String sanitized = text
                .replace(",", "")
                .replace("%", "");

        try {
            return (int) Math.round(Double.parseDouble(sanitized));
        } catch (NumberFormatException exception) {
            log.debug("Failed to parse {} from value '{}' as integer: {}", fieldName, text,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private Boolean getBoolean(Row row, Map<String, Integer> headers, String... aliases) {
        Integer columnIndex = findColumnIndex(headers, aliases);
        if (columnIndex == null) {
            return null;
        }

        String value = normalizeCellText(dataFormatter.formatCellValue(row.getCell(columnIndex)));
        if (value == null) {
            return null;
        }

        String normalized = normalizeHeader(value);
        if (normalized == null) {
            return null;
        }

        return switch (normalized) {
            case "true", "1", "yes", "y", "是", "已转化", "converted" -> true;
            case "false", "0", "no", "n", "否", "未转化", "notconverted" -> false;
            default -> null;
        };
    }

    private LocalDate getDate(String fieldName, Row row, Map<String, Integer> headers, String... aliases) {
        Integer columnIndex = findColumnIndex(headers, aliases);
        if (columnIndex == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String value = normalizeCellText(dataFormatter.formatCellValue(cell));
        if (value == null) {
            return null;
        }

        String trimmed = value;

        if (trimmed.matches("\\d+(\\.0+)?")) {
            try {
                double serial = Double.parseDouble(trimmed);
                return DateUtil.getLocalDateTime(serial).toLocalDate();
            } catch (Exception exception) {
                log.debug("Failed to parse {} from value '{}' as date serial: {}", fieldName, trimmed,
                        exception.getClass().getSimpleName());
                // Fall through to pattern parsing below.
            }
        }

        // ISO datetime with timezone: "2026-03-15T08:54:24.000+0000"
        int tIdx = trimmed.indexOf('T');
        if (tIdx > 0) {
            String datePart = trimmed.substring(0, tIdx);
            try {
                return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException exception) {
                log.debug("Failed to parse {} from value '{}' as date: {}", fieldName, trimmed,
                        exception.getClass().getSimpleName());
                // Fall through to other patterns.
            }
        }

        for (DateTimeFormatter formatter : DATE_PATTERNS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException exception) {
                log.debug("Failed to parse {} from value '{}' as date using pattern {}: {}", fieldName, trimmed,
                        formatter, exception.getClass().getSimpleName());
                // Try the next supported date pattern.
            }
        }

        return null;
    }

    private Integer findColumnIndex(Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            String normalizedAlias = normalizeHeader(alias);
            if (normalizedAlias == null) {
                continue;
            }
            if (headers.containsKey(normalizedAlias)) {
                return headers.get(normalizedAlias);
            }
        }
        // Fallback: contains match for compound names (e.g. SalesRetailer__r.DealerCode__c)
        for (String alias : aliases) {
            String normalizedAlias = normalizeHeader(alias);
            if (normalizedAlias == null) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : headers.entrySet()) {
                if (entry.getKey().contains(normalizedAlias)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private boolean isRowBlank(Row row) {
        if (row == null) {
            return true;
        }

        for (Cell cell : row) {
            String value = dataFormatter.formatCellValue(cell);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCellText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || NULL_MARKERS.contains(trimmed.toLowerCase(java.util.Locale.ROOT))) {
            return null;
        }
        return trimmed;
    }

    private Integer nonNegativeOrNull(
            String sheet,
            String field,
            Integer value,
            ImportQualityTracker tracker
    ) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            tracker.issue(sheet, "invalid_negative_" + field);
            return null;
        }
        return value;
    }

    private void recordMissingCampaignMetrics(
            ImportQualityTracker tracker,
            Integer targetOpportunityAmount,
            Integer actualOpportunityCount,
            Integer targetOrderAmount,
            Integer wonOpportunityCount,
            Integer leadCount,
            Integer totalNewCustomerTarget
    ) {
        recordMissingMetric(tracker, "missing_target_opportunity_amount", targetOpportunityAmount);
        recordMissingMetric(tracker, "missing_actual_opportunity_count", actualOpportunityCount);
        recordMissingMetric(tracker, "missing_target_order_amount", targetOrderAmount);
        recordMissingMetric(tracker, "missing_won_opportunity_count", wonOpportunityCount);
        recordMissingMetric(tracker, "missing_lead_count", leadCount);
        recordMissingMetric(tracker, "missing_new_customer_target", totalNewCustomerTarget);
    }

    private void recordMissingMetric(ImportQualityTracker tracker, String reason, Integer value) {
        if (value == null) {
            tracker.issue(CAMPAIGN_SHEET, reason);
        }
    }

    private String deriveCity(String dealerName) {
        if (dealerName == null || dealerName.isBlank()) {
            return "";
        }
        String trimmed = dealerName.trim();
        String parenthesizedCity = extractParenthesizedSuffix(trimmed, '(', ')');
        if (parenthesizedCity == null) {
            parenthesizedCity = extractParenthesizedSuffix(trimmed, '（', '）');
        }
        if (parenthesizedCity != null) {
            return parenthesizedCity;
        }
        // Extract first word as likely city (e.g. "Beijing Star Motors" → "Beijing")
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            return trimmed.substring(0, spaceIdx);
        }
        return trimmed;
    }

    private String extractParenthesizedSuffix(String value, char openChar, char closeChar) {
        int openIdx = value.lastIndexOf(openChar);
        int closeIdx = value.lastIndexOf(closeChar);
        if (openIdx < 0 || closeIdx <= openIdx) {
            return null;
        }
        String extracted = value.substring(openIdx + 1, closeIdx).trim();
        return extracted.isEmpty() ? null : extracted;
    }

    private String lookupDealerGroup(String dealerCode, Map<String, String> dealerGroupByCode) {
        if (dealerCode == null || dealerCode.isBlank()) {
            return "";
        }
        return dealerGroupByCode.getOrDefault(dealerCode, "");
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replaceAll("[\\s_\\-()/\\\\]+", "")
                .toLowerCase();

        return normalized.isEmpty() ? null : normalized;
    }

    private void seedFallbackData(String batchId, Long tenantId) {
        dealerRepository.saveAll(List.of(
                new Dealer("BJ001", "Beijing Star Motors", "Beijing", "North Star Group", batchId, tenantId),
                new Dealer("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group", batchId, tenantId),
                new Dealer("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group", batchId, tenantId),
                new Dealer("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group", batchId, tenantId),
                new Dealer("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group", batchId, tenantId),
                new Dealer("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group", batchId, tenantId)
        ));

        opportunityRepository.saveAll(List.of(
                new Opportunity("OPP-1001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "未知", "Negotiation", "Test Drive", LocalDate.of(2026, 4, 2),
                        LocalDate.of(2026, 5, 8), 70, batchId, tenantId),
                new Opportunity("OPP-1002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "M7", "未知", "Proposal", "WeChat", LocalDate.of(2026, 4, 6),
                        LocalDate.of(2026, 5, 12), 55, batchId, tenantId),
                new Opportunity("OPP-1003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "未知", "Won", "Showroom", LocalDate.of(2026, 4, 3),
                        LocalDate.of(2026, 4, 24), 100, batchId, tenantId),
                new Opportunity("OPP-1004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "X5", "未知", "Qualified", "Douyin", LocalDate.of(2026, 4, 9),
                        LocalDate.of(2026, 5, 18), 45, batchId, tenantId),
                new Opportunity("OPP-1005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "E3", "未知", "Negotiation", "Referral", LocalDate.of(2026, 4, 11),
                        LocalDate.of(2026, 5, 20), 68, batchId, tenantId),
                new Opportunity("OPP-1006", "CD001", "Chengdu Drive Center", "Chengdu", "West Link Group",
                        "E3", "未知", "Lost", "Website", LocalDate.of(2026, 4, 13),
                        LocalDate.of(2026, 4, 29), 20, batchId, tenantId),
                new Opportunity("OPP-1007", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "未知", "Negotiation", "Campaign", LocalDate.of(2026, 4, 16),
                        LocalDate.of(2026, 5, 21), 75, batchId, tenantId),
                new Opportunity("OPP-1008", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "未知", "Qualified", "Website", LocalDate.of(2026, 4, 18),
                        LocalDate.of(2026, 5, 26), 48, batchId, tenantId)
        ));

        campaignRepository.saveAll(List.of(
                new Campaign("CAM-2001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "Test Drive", LocalDate.of(2026, 3, 15), 28, 35, batchId, tenantId),
                new Campaign("CAM-2002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "M7", "Online Live", LocalDate.of(2026, 3, 21), 19, 30, batchId, tenantId),
                new Campaign("CAM-2003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "City Show", LocalDate.of(2026, 3, 18), 36, 40, batchId, tenantId),
                new Campaign("CAM-2004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "X5", "Referral Drive", LocalDate.of(2026, 3, 27), 22, 28, batchId, tenantId),
                new Campaign("CAM-2005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "E3", "Mall Booth", LocalDate.of(2026, 3, 25), 25, 32, batchId, tenantId)
        ));

        taskRepository.saveAll(List.of(
                new Task("TSK-3001", "BJ001", "Beijing Star Motors", "Beijing",
                        "North Star Group", "OPP-1001", "未知", "Completed", LocalDate.of(2026, 4, 3), batchId, tenantId),
                new Task("TSK-3002", "BJ002", "Beijing Horizon Auto", "Beijing",
                        "North Star Group", "OPP-1002", "未知", "Pending", LocalDate.of(2026, 4, 7), batchId, tenantId),
                new Task("TSK-3003", "SH001", "Shanghai Prime Mobility", "Shanghai",
                        "East River Group", "OPP-1003", "未知", "Completed", LocalDate.of(2026, 4, 4), batchId, tenantId),
                new Task("TSK-3004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou",
                        "East River Group", "OPP-1004", "未知", "In Progress", LocalDate.of(2026, 4, 10), batchId, tenantId),
                new Task("TSK-3005", "GZ001", "Guangzhou Motion Hub", "Guangzhou",
                        "South Bay Group", "OPP-1005", "未知", "Pending", LocalDate.of(2026, 4, 12), batchId, tenantId),
                new Task("TSK-3006", "CD001", "Chengdu Drive Center", "Chengdu",
                        "West Link Group", "OPP-1006", "未知", "Overdue", LocalDate.of(2026, 4, 14), batchId, tenantId)
        ));

        targetRepository.saveAll(List.of(
                new Target("BJ001", "Beijing Star Motors", "Beijing", "North Star Group", "M7",
                        2026, 4, 120, 92, 110, batchId, tenantId),
                new Target("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group", "M7",
                        2026, 4, 100, 68, 80, batchId, tenantId),
                new Target("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group", "X5",
                        2026, 4, 130, 126, 145, batchId, tenantId),
                new Target("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group", "X5",
                        2026, 4, 110, 97, 115, batchId, tenantId),
                new Target("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group", "E3",
                        2026, 4, 105, 88, 100, batchId, tenantId),
                new Target("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group", "E3",
                        2026, 4, 95, 61, 72, batchId, tenantId)
        ));

        leadRepository.saveAll(List.of(
                new Lead("LED-4001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "WeChat", "Qualified", "M7", LocalDate.of(2026, 3, 28), true, batchId, tenantId),
                new Lead("LED-4002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "Douyin", "New", "M7", LocalDate.of(2026, 4, 1), false, batchId, tenantId),
                new Lead("LED-4003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "Showroom", "Converted", "X5", LocalDate.of(2026, 3, 30), true, batchId, tenantId),
                new Lead("LED-4004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "Campaign", "Qualified", "X5", LocalDate.of(2026, 4, 5), false, batchId, tenantId),
                new Lead("LED-4005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "Referral", "Qualified", "E3", LocalDate.of(2026, 4, 8), true, batchId, tenantId),
                new Lead("LED-4006", "CD001", "Chengdu Drive Center", "Chengdu", "West Link Group",
                        "Website", "Lost", "E3", LocalDate.of(2026, 4, 9), false, batchId, tenantId),
                new Lead("LED-4007", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "Xiaohongshu", "New", "X5", LocalDate.of(2026, 4, 12), false, batchId, tenantId),
                new Lead("LED-4008", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "Website", "Qualified", "M7", LocalDate.of(2026, 4, 14), true, batchId, tenantId)
        ));
    }

    private record HeaderInfo(int headerRowIndex, Map<String, Integer> headers) {
    }

    private record ParsedWorkbook(
            List<Dealer> dealers,
            List<Opportunity> opportunities,
            List<Campaign> campaigns,
            List<Task> tasks,
            List<Target> targets,
            List<Lead> leads
    ) {
        void requireTenant(Long tenantId) {
            Stream.of(dealers, opportunities, campaigns, tasks, targets, leads)
                    .flatMap(List::stream)
                    .map(row -> (com.brand.agentpoc.tenant.domain.TenantScoped) row)
                    .filter(row -> !tenantId.equals(row.getTenantId()))
                    .findFirst()
                    .ifPresent(row -> {
                        throw new IllegalArgumentException("Imported rows must belong to the selected tenant.");
                    });
        }

        boolean isEmpty() {
            return dealers.isEmpty()
                    && opportunities.isEmpty()
                    && campaigns.isEmpty()
                    && tasks.isEmpty()
                    && targets.isEmpty()
                    && leads.isEmpty();
        }
    }
}
