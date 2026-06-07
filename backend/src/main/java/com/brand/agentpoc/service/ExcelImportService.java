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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final DataFormatter dataFormatter = new DataFormatter();
    private final AtomicInteger campaignRowsProcessedCount = new AtomicInteger();
    private final AtomicInteger normalizedRowsCount = new AtomicInteger();

    public ExcelImportService(
            AppProperties appProperties,
            ResourceLoader resourceLoader,
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository
    ) {
        this.appProperties = appProperties;
        this.resourceLoader = resourceLoader;
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hasExistingData()) {
            log.info("Sample data already initialized, skipping startup import.");
            return;
        }

        Resource resource = resolveConfiguredResource(appProperties.getExcel().getPath());
        boolean imported = false;

        if (resource != null && resource.exists()) {
            imported = importWorkbook(resource);
        } else {
            log.warn("Excel resource not found at {}. Seeding built-in sample data instead.",
                    appProperties.getExcel().getPath());
        }

        if (!imported) {
            seedFallbackData();
            log.info("Fallback sample data seeded.");
        }

        log.info(
                "Data initialization completed. dealers={}, opportunities={}, campaigns={}, tasks={}, targets={}, leads={}",
                dealerRepository.count(),
                opportunityRepository.count(),
                campaignRepository.count(),
                taskRepository.count(),
                targetRepository.count(),
                leadRepository.count()
        );
    }

    private boolean hasExistingData() {
        return dealerRepository.count() > 0
                || opportunityRepository.count() > 0
                || campaignRepository.count() > 0
                || taskRepository.count() > 0
                || targetRepository.count() > 0
                || leadRepository.count() > 0;
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

    private boolean importWorkbook(Resource resource) {
        log.info("Attempting workbook import from {}", resource);
        campaignRowsProcessedCount.set(0);
        normalizedRowsCount.set(0);

        try (InputStream inputStream = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            ParsedWorkbook parsedWorkbook = parseWorkbook(workbook);
            if (parsedWorkbook.isEmpty()) {
                log.warn("Workbook import produced no usable rows. Falling back to built-in sample data.");
                return false;
            }

            persistParsedWorkbook(parsedWorkbook);

            log.info(
                    "Workbook import completed. dealers={}, opportunities={}, campaigns={}, tasks={}, targets={}, leads={}",
                    parsedWorkbook.dealers().size(),
                    parsedWorkbook.opportunities().size(),
                    parsedWorkbook.campaigns().size(),
                    parsedWorkbook.tasks().size(),
                    parsedWorkbook.targets().size(),
                    parsedWorkbook.leads().size()
            );
            return true;
        } catch (Exception exception) {
            log.error("Workbook import failed. Falling back to built-in sample data.", exception);
            return false;
        } finally {
            log.info(
                    "[Import-Normalization] Campaign import completed. Total rows processed: {}, rows with defaulted non-critical fields: {}.",
                    campaignRowsProcessedCount.get(),
                    normalizedRowsCount.get()
            );
        }
    }

    private ParsedWorkbook parseWorkbook(Workbook workbook) {
        // Parse AE Target Data first to extract dealer group name lookup
        Map<String, String> dealerGroupByCode = new LinkedHashMap<>();
        List<Target> targets = parseTargetSheet(findSheet(workbook, "AE Target Data", "Target", "Targets"), dealerGroupByCode);

        // Parse sheets that have direct dealer info
        List<Opportunity> opportunities = parseOpportunitySheet(
                findSheet(workbook, "Opportunity", "Opportunities"), dealerGroupByCode);
        List<Campaign> campaigns = parseCampaignSheet(
                findSheet(workbook, "Campaign", "Campaigns"), dealerGroupByCode);
        List<Lead> leads = parseLeadSheet(
                findSheet(workbook, "Lead", "Leads"), dealerGroupByCode);

        // Task sheet has no direct dealer info — join via Opportunity
        Map<String, String[]> oppDealerInfo = new LinkedHashMap<>();
        for (Opportunity opp : opportunities) {
            oppDealerInfo.put(opp.getOpportunityId(),
                    new String[]{opp.getDealerCode(), opp.getDealerName(), opp.getCity(), opp.getDealerGroupName()});
        }
        List<Task> tasks = parseTaskSheet(
                findSheet(workbook, "Task", "Tasks"), oppDealerInfo, dealerGroupByCode);

        List<Dealer> dealers = deriveDealers(opportunities, campaigns, tasks, targets, leads);
        return new ParsedWorkbook(dealers, opportunities, campaigns, tasks, targets, leads);
    }

    private void persistParsedWorkbook(ParsedWorkbook parsedWorkbook) {
        dealerRepository.saveAll(parsedWorkbook.dealers());
        opportunityRepository.saveAll(parsedWorkbook.opportunities());
        campaignRepository.saveAll(parsedWorkbook.campaigns());
        taskRepository.saveAll(parsedWorkbook.tasks());
        targetRepository.saveAll(parsedWorkbook.targets());
        leadRepository.saveAll(parsedWorkbook.leads());
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

    private List<Opportunity> parseOpportunitySheet(Sheet sheet, Map<String, String> dealerGroupByCode) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Opportunity> items = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }

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
            if (expectedCloseDate == null) {
                expectedCloseDate = createdDate != null ? createdDate.plusDays(30) : null;
            }
            Integer probability = getInteger("probability", row, headerInfo.headers(), "probability", "成交概率", "赢单概率", "概率");

            if (hasBlank(opportunityId, dealerCode, dealerName, stageName)
                    || createdDate == null
                    || expectedCloseDate == null
                    || probability == null) {
                log.debug("Skipping opportunity row {} due to missing required values.", rowIndex + 1);
                continue;
            }

            if (productModel == null) {
                productModel = "未知";
            }
            if (purchaseHorizon == null) {
                purchaseHorizon = "未知";
            }
            if (leadSource == null) {
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
                    probability
            ));
        }

        return items;
    }

    private List<Campaign> parseCampaignSheet(Sheet sheet, Map<String, String> dealerGroupByCode) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Campaign> items = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }
            campaignRowsProcessedCount.incrementAndGet();

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

            boolean normalized = false;
            if (campaignName == null) {
                campaignName = campaignId;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: campaignName is blank, defaulting to campaignId", rowIndex + 1);
            }
            if (eventType == null) {
                eventType = "0";
                normalized = true;
                log.debug("[Import-Normalization] Row {}: eventType is blank, defaulting to '0'", rowIndex + 1);
            }
            if (campaignType == null) {
                campaignType = "0";
                normalized = true;
                log.debug("[Import-Normalization] Row {}: campaignType is blank, defaulting to '0'", rowIndex + 1);
            }
            if (dealerCode == null) {
                dealerCode = "未分配";
                normalized = true;
                log.debug("[Import-Normalization] Row {}: dealerCode is blank, defaulting to '未分配'", rowIndex + 1);
            }
            if (dealerName == null) {
                dealerName = "未分配";
                normalized = true;
                log.debug("[Import-Normalization] Row {}: dealerName is blank, defaulting to '未分配'", rowIndex + 1);
            }
            if (actualOpportunityCount == null) {
                actualOpportunityCount = 0;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: actualOpportunityCount is blank, defaulting to 0", rowIndex + 1);
            }
            if (targetOpportunityAmount == null) {
                targetOpportunityAmount = totalNewCustomerTarget != null ? totalNewCustomerTarget : 0;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: targetOpportunityAmount is blank, defaulting to {}", rowIndex + 1, targetOpportunityAmount);
            }
            if (targetOrderAmount == null) {
                targetOrderAmount = 0;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: targetOrderAmount is blank, defaulting to 0", rowIndex + 1);
            }
            if (wonOpportunityCount == null) {
                wonOpportunityCount = 0;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: wonOpportunityCount is blank, defaulting to 0", rowIndex + 1);
            }
            if (leadCount == null) {
                leadCount = 0;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: leadCount is blank, defaulting to 0", rowIndex + 1);
            }
            if (totalNewCustomerTarget == null) {
                totalNewCustomerTarget = targetOpportunityAmount;
                normalized = true;
                log.debug("[Import-Normalization] Row {}: totalNewCustomerTarget is blank, defaulting to targetOpportunityAmount", rowIndex + 1);
            }
            if (productModel == null) {
                productModel = "未知";
                normalized = true;
                log.debug("[Import-Normalization] Row {}: productModel is blank, defaulting to '未知'", rowIndex + 1);
            }
            if (normalized) {
                normalizedRowsCount.incrementAndGet();
            }

            if (hasBlank(campaignId) || createdDate == null) {
                log.debug("Skipping campaign row {} due to missing required values.", rowIndex + 1);
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
                    totalNewCustomerTarget
            ));
        }

        return items;
    }

    private List<Task> parseTaskSheet(Sheet sheet, Map<String, String[]> oppDealerInfo,
            Map<String, String> dealerGroupByCode) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Task> items = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }

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
                log.debug("Skipping task row {} due to missing required values.", rowIndex + 1);
                continue;
            }
            if (subject == null) {
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
                    createdDate
            ));
        }

        return items;
    }

    private List<Target> parseTargetSheet(Sheet sheet, Map<String, String> dealerGroupByCode) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Target> items = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }

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
            if (asKTarget == null) {
                asKTarget = 0;
            }
            Integer opportunityWonCount = getInteger("opportunityWonCount", row, headerInfo.headers(),
                    "opportunitywoncount", "成交商机数", "已成交商机数", "赢单数", "opportunitywoncountc");
            Integer opportunityCreateCount = getInteger("opportunityCreateCount", row, headerInfo.headers(),
                    "opportunitycreatecount", "商机创建数", "商机创建数量",
                    "opportunitycreatecountc");
            if (opportunityCreateCount == null) {
                opportunityCreateCount = 0;
            }

            if (hasBlank(dealerCode, dealerName, productModel)
                    || targetYear == null
                    || targetMonth == null
                    || opportunityWonCount == null) {
                log.debug("Skipping target row {} due to missing required values.", rowIndex + 1);
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
                    opportunityCreateCount
            ));
        }

        return items;
    }

    private List<Lead> parseLeadSheet(Sheet sheet, Map<String, String> dealerGroupByCode) {
        if (sheet == null) {
            return List.of();
        }

        HeaderInfo headerInfo = detectHeaderInfo(sheet);
        if (headerInfo == null) {
            return List.of();
        }

        List<Lead> items = new ArrayList<>();
        for (int rowIndex = headerInfo.headerRowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowBlank(row)) {
                continue;
            }

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
                log.debug("Skipping lead row {} due to missing required values.", rowIndex + 1);
                continue;
            }

            if (dealerCode == null) {
                dealerCode = "未分配";
            }
            if (dealerName == null) {
                dealerName = "未分配";
            }
            if (productModel == null) {
                productModel = "未知";
            }
            if (leadSource == null) {
                leadSource = "未知";
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
                    converted
            ));
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
            List<Lead> leads
    ) {
        Map<String, Dealer> dealers = new LinkedHashMap<>();

        opportunities.forEach(opportunity -> addDealer(dealers,
                opportunity.getDealerCode(), opportunity.getDealerName(), opportunity.getCity(), opportunity.getDealerGroupName()));
        campaigns.forEach(campaign -> addDealer(dealers,
                campaign.getDealerCode(), campaign.getDealerName(), campaign.getCity(), campaign.getDealerGroupName()));
        tasks.forEach(task -> addDealer(dealers,
                task.getDealerCode(), task.getDealerName(), task.getCity(), task.getDealerGroupName()));
        targets.forEach(target -> addDealer(dealers,
                target.getDealerCode(), target.getDealerName(), target.getCity(), target.getDealerGroupName()));
        leads.forEach(lead -> addDealer(dealers,
                lead.getDealerCode(), lead.getDealerName(), lead.getCity(), lead.getDealerGroupName()));

        return new ArrayList<>(dealers.values());
    }

    private void addDealer(Map<String, Dealer> dealers, String dealerCode, String dealerName, String city, String dealerGroupName) {
        if (hasBlank(dealerCode, dealerName, city)) {
            return;
        }
        dealers.putIfAbsent(dealerCode, new Dealer(dealerCode, dealerName, city,
                dealerGroupName != null ? dealerGroupName : ""));
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

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

        String text = dataFormatter.formatCellValue(cell);
        if (text == null) {
            return null;
        }

        String sanitized = text.trim()
                .replace(",", "")
                .replace("%", "");
        if (sanitized.isEmpty()) {
            return null;
        }

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

        String value = dataFormatter.formatCellValue(row.getCell(columnIndex));
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

        String value = dataFormatter.formatCellValue(cell);
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

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

    private void seedFallbackData() {
        dealerRepository.saveAll(List.of(
                new Dealer("BJ001", "Beijing Star Motors", "Beijing", "North Star Group"),
                new Dealer("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group"),
                new Dealer("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group"),
                new Dealer("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group"),
                new Dealer("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group"),
                new Dealer("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group")
        ));

        opportunityRepository.saveAll(List.of(
                new Opportunity("OPP-1001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "Negotiation", "Test Drive", LocalDate.of(2026, 4, 2), LocalDate.of(2026, 5, 8), 70),
                new Opportunity("OPP-1002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "M7", "Proposal", "WeChat", LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 12), 55),
                new Opportunity("OPP-1003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "Won", "Showroom", LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 24), 100),
                new Opportunity("OPP-1004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "X5", "Qualified", "Douyin", LocalDate.of(2026, 4, 9), LocalDate.of(2026, 5, 18), 45),
                new Opportunity("OPP-1005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "E3", "Negotiation", "Referral", LocalDate.of(2026, 4, 11), LocalDate.of(2026, 5, 20), 68),
                new Opportunity("OPP-1006", "CD001", "Chengdu Drive Center", "Chengdu", "West Link Group",
                        "E3", "Lost", "Website", LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 29), 20),
                new Opportunity("OPP-1007", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "Negotiation", "Campaign", LocalDate.of(2026, 4, 16), LocalDate.of(2026, 5, 21), 75),
                new Opportunity("OPP-1008", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "Qualified", "Website", LocalDate.of(2026, 4, 18), LocalDate.of(2026, 5, 26), 48)
        ));

        campaignRepository.saveAll(List.of(
                new Campaign("CAM-2001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "M7", "Test Drive", LocalDate.of(2026, 3, 15), 28, 35),
                new Campaign("CAM-2002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "M7", "Online Live", LocalDate.of(2026, 3, 21), 19, 30),
                new Campaign("CAM-2003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "X5", "City Show", LocalDate.of(2026, 3, 18), 36, 40),
                new Campaign("CAM-2004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "X5", "Referral Drive", LocalDate.of(2026, 3, 27), 22, 28),
                new Campaign("CAM-2005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "E3", "Mall Booth", LocalDate.of(2026, 3, 25), 25, 32)
        ));

        taskRepository.saveAll(List.of(
                new Task("TSK-3001", "BJ001", "Beijing Star Motors", "Beijing",
                        "North Star Group", "OPP-1001", "Completed", LocalDate.of(2026, 4, 3)),
                new Task("TSK-3002", "BJ002", "Beijing Horizon Auto", "Beijing",
                        "North Star Group", "OPP-1002", "Pending", LocalDate.of(2026, 4, 7)),
                new Task("TSK-3003", "SH001", "Shanghai Prime Mobility", "Shanghai",
                        "East River Group", "OPP-1003", "Completed", LocalDate.of(2026, 4, 4)),
                new Task("TSK-3004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou",
                        "East River Group", "OPP-1004", "In Progress", LocalDate.of(2026, 4, 10)),
                new Task("TSK-3005", "GZ001", "Guangzhou Motion Hub", "Guangzhou",
                        "South Bay Group", "OPP-1005", "Pending", LocalDate.of(2026, 4, 12)),
                new Task("TSK-3006", "CD001", "Chengdu Drive Center", "Chengdu",
                        "West Link Group", "OPP-1006", "Overdue", LocalDate.of(2026, 4, 14))
        ));

        targetRepository.saveAll(List.of(
                new Target("BJ001", "Beijing Star Motors", "Beijing", "North Star Group", "M7", 2026, 4, 120, 92, 110),
                new Target("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group", "M7", 2026, 4, 100, 68, 80),
                new Target("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group", "X5", 2026, 4, 130, 126, 145),
                new Target("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group", "X5", 2026, 4, 110, 97, 115),
                new Target("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group", "E3", 2026, 4, 105, 88, 100),
                new Target("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group", "E3", 2026, 4, 95, 61, 72)
        ));

        leadRepository.saveAll(List.of(
                new Lead("LED-4001", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "WeChat", "Qualified", "M7", LocalDate.of(2026, 3, 28), true),
                new Lead("LED-4002", "BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group",
                        "Douyin", "New", "M7", LocalDate.of(2026, 4, 1), false),
                new Lead("LED-4003", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "Showroom", "Converted", "X5", LocalDate.of(2026, 3, 30), true),
                new Lead("LED-4004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group",
                        "Campaign", "Qualified", "X5", LocalDate.of(2026, 4, 5), false),
                new Lead("LED-4005", "GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group",
                        "Referral", "Qualified", "E3", LocalDate.of(2026, 4, 8), true),
                new Lead("LED-4006", "CD001", "Chengdu Drive Center", "Chengdu", "West Link Group",
                        "Website", "Lost", "E3", LocalDate.of(2026, 4, 9), false),
                new Lead("LED-4007", "SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group",
                        "Xiaohongshu", "New", "X5", LocalDate.of(2026, 4, 12), false),
                new Lead("LED-4008", "BJ001", "Beijing Star Motors", "Beijing", "North Star Group",
                        "Website", "Qualified", "M7", LocalDate.of(2026, 4, 14), true)
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
