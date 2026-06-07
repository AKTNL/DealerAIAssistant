package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.detail.*;
import com.brand.agentpoc.dto.metrics.*;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.entity.*;
import com.brand.agentpoc.repository.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsApiService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsApiService.class);

    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;

    public AnalyticsApiService(
            DealerRepository dealerRepository,
            OpportunityRepository opportunityRepository,
            CampaignRepository campaignRepository,
            TaskRepository taskRepository,
            TargetRepository targetRepository,
            LeadRepository leadRepository
    ) {
        this.dealerRepository = dealerRepository;
        this.opportunityRepository = opportunityRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.targetRepository = targetRepository;
        this.leadRepository = leadRepository;
    }

    // ── Targets ────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<TargetMetrics> getTargetMetrics(Integer year, Integer month, String productModel, String dealerCode) {
        return getTargetMetrics(year, month, productModel, dealerCode, null, null, null);
    }

    @Transactional(readOnly = true)
    public ApiResult<TargetMetrics> getTargetMetrics(
            Integer year,
            Integer month,
            String productModel,
            String dealerCode,
            String city,
            String dealerName,
            String dealerGroupName
    ) {
        List<Target> all = filterTargets(year, month, productModel, dealerCode, city, dealerName, dealerGroupName);

        if (all.isEmpty()) {
            return ApiResult.success(new TargetMetrics(0, 0, 0, 0.0, null, null));
        }

        Map<String, List<Target>> byDealer = all.stream()
                .collect(Collectors.groupingBy(Target::getDealerCode));

        int totalAsKTarget = 0;
        int totalWon = 0;
        TargetMetrics.DealerMetric lowest = null;
        TargetMetrics.DealerMetric highest = null;

        for (var entry : byDealer.entrySet()) {
            String code = entry.getKey();
            List<Target> rows = entry.getValue();
            int sumTarget = rows.stream().mapToInt(Target::getAsKTarget).sum();
            int sumWon = rows.stream().mapToInt(Target::getOpportunityWonCount).sum();
            double rate = sumTarget == 0 ? 0.0 : (double) sumWon / sumTarget * 100.0;
            String name = rows.getFirst().getDealerName();

            totalAsKTarget += sumTarget;
            totalWon += sumWon;

            TargetMetrics.DealerMetric dm = new TargetMetrics.DealerMetric(code, name, Math.round(rate * 10.0) / 10.0);
            if (lowest == null || dm.achievementRate() < lowest.achievementRate()) lowest = dm;
            if (highest == null || dm.achievementRate() > highest.achievementRate()) highest = dm;
        }

        double avgRate = totalAsKTarget == 0 ? 0.0
                : Math.round((double) totalWon / totalAsKTarget * 1000.0) / 10.0;

        return ApiResult.success(new TargetMetrics(
                byDealer.size(), totalAsKTarget, totalWon, avgRate, lowest, highest));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<TargetDetail>> getTargetDetails(
            Integer year, Integer month, String productModel, String dealerCode,
            int page, int pageSize, String sortBy, String sortOrder
    ) {
        return getTargetDetails(year, month, productModel, dealerCode, null, null, null,
                page, pageSize, sortBy, sortOrder);
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<TargetDetail>> getTargetDetails(
            Integer year,
            Integer month,
            String productModel,
            String dealerCode,
            String city,
            String dealerName,
            String dealerGroupName,
            int page,
            int pageSize,
            String sortBy,
            String sortOrder
    ) {
        List<Target> all = filterTargets(year, month, productModel, dealerCode, city, dealerName, dealerGroupName);

        all = sortTargets(all, sortBy, sortOrder);
        return paginateTargets(all, page, pageSize);
    }

    private List<Target> filterTargets(
            Integer year,
            Integer month,
            String productModel,
            String dealerCode,
            String city,
            String dealerName,
            String dealerGroupName
    ) {
        return loadTargets(year, month, productModel, dealerCode).stream()
                .filter(t -> year == null || t.getTargetYear().equals(year))
                .filter(t -> month == null || t.getTargetMonth().equals(month))
                .filter(t -> matchesExact(t.getProductModel(), productModel))
                .filter(t -> matchesExact(t.getDealerCode(), dealerCode))
                .filter(t -> matchesContains(t.getCity(), city))
                .filter(t -> matchesContains(t.getDealerName(), dealerName))
                .filter(t -> matchesContains(t.getDealerGroupName(), dealerGroupName))
                .toList();
    }

    private List<Target> loadTargets(Integer year, Integer month, String productModel, String dealerCode) {
        if (hasText(dealerCode)) {
            return targetRepository.findByDealerCodeIgnoreCase(dealerCode.trim());
        }
        if (year != null && month != null) {
            return targetRepository.findByTargetYearAndTargetMonth(year, month);
        }
        if (year != null) {
            return targetRepository.findByTargetYear(year);
        }
        if (hasText(productModel)) {
            return targetRepository.findByProductModelIgnoreCase(productModel.trim());
        }
        return targetRepository.findAll();
    }

    // ── Opportunities ──────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<OpportunityMetrics> getOpportunityMetrics(String dealerCode, String startDate, String endDate) {
        List<Opportunity> all = filterOpportunities(dealerCode, startDate, endDate);

        if (all.isEmpty()) {
            return ApiResult.success(new OpportunityMetrics(0, 0, 0, 0, 0.0,
                    Map.of(), List.of()));
        }

        Map<String, Long> stageDistribution = all.stream()
                .collect(Collectors.groupingBy(Opportunity::getStageName, Collectors.counting()));

        long won = all.stream().filter(o -> "Closed Won".equalsIgnoreCase(o.getStageName())).count();
        long lost = all.stream().filter(o -> "Closed Lost".equalsIgnoreCase(o.getStageName())).count();
        long closed = won + lost;
        double winRate = closed == 0 ? 0.0 : Math.round((double) won / closed * 1000.0) / 10.0;

        // Opportunities don't have a closedReason field in the entity.
        // We'll use stageName + leadSource as loss attribution.
        List<OpportunityMetrics.LossReason> topLossReasons = all.stream()
                .filter(o -> "Closed Lost".equalsIgnoreCase(o.getStageName()))
                .collect(Collectors.groupingBy(
                        o -> (o.getLeadSource() != null ? o.getLeadSource() : "Unknown"),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new OpportunityMetrics.LossReason(e.getKey(), e.getValue()))
                .toList();

        return ApiResult.success(new OpportunityMetrics(
                all.size(), (int) won, (int) lost,
                all.size() - (int) closed, winRate, stageDistribution, topLossReasons));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<OpportunityDetail>> getOpportunityDetails(
            String dealerCode, String startDate, String endDate,
            String keyword, String stageName, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Opportunity> all = filterOpportunities(dealerCode, startDate, endDate);
        all = all.stream()
                .filter(o -> keyword == null || containsKeyword(o, keyword))
                .filter(o -> stageName == null || stageName.equals(o.getStageName()))
                .toList();

        all = sortOpportunities(all, sortBy, sortOrder);
        return paginate(all, this::toOpportunityDetail, page, pageSize);
    }

    // ── Leads ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<LeadMetrics> getLeadMetrics(String leadSource, String dealerCode) {
        List<Lead> all = loadLeads(leadSource, dealerCode).stream()
                .filter(l -> leadSource == null || leadSource.equals(l.getLeadSource()))
                .filter(l -> dealerCode == null || dealerCode.equals(l.getDealerCode()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new LeadMetrics(0, 0, 0.0, Map.of(), null));
        }

        Map<String, Long> sourceDistribution = all.stream()
                .collect(Collectors.groupingBy(Lead::getLeadSource, Collectors.counting()));

        long converted = all.stream().filter(Lead::getConverted).count();
        double conversionRate = all.isEmpty() ? 0.0
                : Math.round((double) converted / all.size() * 1000.0) / 10.0;

        LeadMetrics.SourceMetric bestSource = all.stream()
                .filter(l -> l.getLeadSource() != null)
                .collect(Collectors.groupingBy(Lead::getLeadSource))
                .entrySet().stream()
                .map(e -> {
                    long c = e.getValue().stream().filter(Lead::getConverted).count();
                    double r = e.getValue().isEmpty() ? 0.0 : (double) c / e.getValue().size() * 100.0;
                    return new LeadMetrics.SourceMetric(e.getKey(), Math.round(r * 10.0) / 10.0);
                })
                .max(Comparator.comparing(LeadMetrics.SourceMetric::conversionRate))
                .orElse(null);

        return ApiResult.success(new LeadMetrics(all.size(), (int) converted, conversionRate,
                sourceDistribution, bestSource));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<LeadDetail>> getLeadDetails(
            String leadSource, String dealerCode, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Lead> all = loadLeads(leadSource, dealerCode).stream()
                .filter(l -> leadSource == null || leadSource.equals(l.getLeadSource()))
                .filter(l -> dealerCode == null || dealerCode.equals(l.getDealerCode()))
                .toList();

        all = sortLeads(all, sortBy, sortOrder);
        return paginate(all, this::toLeadDetail, page, pageSize);
    }

    // ── Tasks ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<TaskMetrics> getTaskMetrics(String dealerCode) {
        List<Task> all = loadTasks(dealerCode).stream()
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new TaskMetrics(0, 0, 0, 0, 0.0, null));
        }

        long completed = all.stream().filter(t -> "Completed".equalsIgnoreCase(t.getStatus())).count();
        long overdue = all.stream().filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();
        long open = all.size() - completed - overdue;
        double completionRate = all.isEmpty() ? 0.0
                : Math.round((double) completed / all.size() * 1000.0) / 10.0;

        TaskMetrics.BacklogDealer highestBacklog = all.stream()
                .filter(t -> !"Completed".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.groupingBy(Task::getDealerCode))
                .entrySet().stream()
                .map(e -> {
                    long o = e.getValue().stream()
                            .filter(t -> !"Overdue".equalsIgnoreCase(t.getStatus())
                                    && !"Completed".equalsIgnoreCase(t.getStatus())).count();
                    long ov = e.getValue().stream()
                            .filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();
                    return new TaskMetrics.BacklogDealer(e.getKey(),
                            e.getValue().getFirst().getDealerName(), (int) o, (int) ov);
                })
                .max(Comparator.comparingInt(b -> b.openCount() + b.overdueCount()))
                .orElse(null);

        return ApiResult.success(new TaskMetrics(all.size(), (int) completed,
                (int) open, (int) overdue, completionRate, highestBacklog));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<TaskDetail>> getTaskDetails(
            String dealerCode, String keyword, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Task> all = loadTasks(dealerCode).stream()
                .filter(t -> dealerCode == null || dealerCode.equals(t.getDealerCode()))
                .filter(t -> keyword == null || t.getTaskId().contains(keyword)
                        || t.getDealerName().contains(keyword))
                .toList();

        all = sortTasks(all, sortBy, sortOrder);
        return paginate(all, this::toTaskDetail, page, pageSize);
    }

    // ── Campaigns ──────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResult<CampaignMetrics> getCampaignMetrics(String campaignType) {
        List<Campaign> all = loadCampaigns(campaignType).stream()
                .filter(c -> campaignType == null || campaignType.equals(c.getCampaignType()))
                .toList();

        if (all.isEmpty()) {
            return ApiResult.success(new CampaignMetrics(0, 0.0, null, 0, 0));
        }

        int totalTarget = all.stream().mapToInt(Campaign::getTotalNewCustomerTarget).sum();
        int totalActual = all.stream().mapToInt(Campaign::getActualOpportunityCount).sum();
        double avgAttainment = totalTarget == 0 ? 0.0
                : Math.round((double) totalActual / totalTarget * 1000.0) / 10.0;

        CampaignMetrics.BestCampaign best = all.stream()
                .map(c -> {
                    double rate = c.getTotalNewCustomerTarget() == 0 ? 0.0
                            : (double) c.getActualOpportunityCount() / c.getTotalNewCustomerTarget() * 100.0;
                    return new CampaignMetrics.BestCampaign(c.getCampaignId(), c.getCampaignId(),
                            Math.round(rate * 10.0) / 10.0);
                })
                .max(Comparator.comparing(CampaignMetrics.BestCampaign::attainmentRate))
                .orElse(null);

        return ApiResult.success(new CampaignMetrics(all.size(), avgAttainment, best, totalActual, totalTarget));
    }

    @Transactional(readOnly = true)
    public ApiResult<ApiPage<CampaignDetail>> getCampaignDetails(
            String campaignType, String keyword, int page, int pageSize, String sortBy, String sortOrder
    ) {
        List<Campaign> all = loadCampaigns(campaignType).stream()
                .filter(c -> campaignType == null || campaignType.equals(c.getCampaignType()))
                .filter(c -> keyword == null || c.getCampaignId().contains(keyword)
                        || c.getDealerName().contains(keyword))
                .toList();

        all = sortCampaigns(all, sortBy, sortOrder);
        return paginate(all, this::toCampaignDetail, page, pageSize);
    }

    // ── Private helpers ────────────────────────────────

    private int clampPageSize(int pageSize) {
        return Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    private <E, D> ApiResult<ApiPage<D>> paginate(List<E> all, Function<E, D> mapper, int page, int pageSize) {
        long total = all.size();
        int safeSize = clampPageSize(pageSize);
        int fromIndex = (page - 1) * safeSize;
        List<D> items;
        if (fromIndex < 0 || fromIndex >= total) {
            items = List.of();
        } else {
            int toIndex = Math.min(fromIndex + safeSize, (int) total);
            items = all.subList(fromIndex, toIndex).stream().map(mapper).toList();
        }
        return ApiResult.success(ApiPage.of(items, total, page, safeSize));
    }

    private ApiResult<ApiPage<TargetDetail>> paginateTargets(List<Target> all, int page, int pageSize) {
        long total = all.size();
        int safeSize = clampPageSize(pageSize);
        int fromIndex = (page - 1) * safeSize;
        List<TargetDetail> items;
        if (fromIndex < 0 || fromIndex >= total) {
            items = List.of();
        } else {
            int toIndex = Math.min(fromIndex + safeSize, (int) total);
            items = all.subList(fromIndex, toIndex).stream().map(this::toTargetDetail).toList();
        }
        return ApiResult.success(ApiPage.of(items, total, page, safeSize));
    }

    private List<Opportunity> filterOpportunities(String dealerCode, String startDate, String endDate) {
        LocalDate start = parseDate("startDate", startDate);
        LocalDate end = parseDate("endDate", endDate);
        return loadOpportunities(dealerCode, start, end).stream()
                .filter(o -> dealerCode == null || dealerCode.equals(o.getDealerCode()))
                .filter(o -> {
                    if (start == null && end == null) return true;
                    return (start == null || !o.getCreatedDate().isBefore(start))
                            && (end == null || !o.getCreatedDate().isAfter(end));
                })
                .toList();
    }

    private List<Opportunity> loadOpportunities(String dealerCode, LocalDate startDate, LocalDate endDate) {
        if (hasText(dealerCode) && startDate != null && endDate != null) {
            return opportunityRepository.findByDealerCodeIgnoreCaseAndCreatedDateBetween(
                    dealerCode.trim(),
                    startDate,
                    endDate
            );
        }
        if (hasText(dealerCode)) {
            return opportunityRepository.findByDealerCodeIgnoreCase(dealerCode.trim());
        }
        if (startDate != null && endDate != null) {
            return opportunityRepository.findByCreatedDateBetween(startDate, endDate);
        }
        if (startDate != null) {
            return opportunityRepository.findByCreatedDateGreaterThanEqual(startDate);
        }
        if (endDate != null) {
            return opportunityRepository.findByCreatedDateLessThanEqual(endDate);
        }
        return opportunityRepository.findAll();
    }

    private List<Lead> loadLeads(String leadSource, String dealerCode) {
        if (hasText(leadSource) && hasText(dealerCode)) {
            return leadRepository.findByLeadSourceIgnoreCaseAndDealerCodeIgnoreCase(leadSource.trim(), dealerCode.trim());
        }
        if (hasText(leadSource)) {
            return leadRepository.findByLeadSourceIgnoreCase(leadSource.trim());
        }
        if (hasText(dealerCode)) {
            return leadRepository.findByDealerCodeIgnoreCase(dealerCode.trim());
        }
        return leadRepository.findAll();
    }

    private List<Task> loadTasks(String dealerCode) {
        if (hasText(dealerCode)) {
            return taskRepository.findByDealerCodeIgnoreCase(dealerCode.trim());
        }
        return taskRepository.findAll();
    }

    private List<Campaign> loadCampaigns(String campaignType) {
        if (hasText(campaignType)) {
            return campaignRepository.findByCampaignTypeIgnoreCase(campaignType.trim());
        }
        return campaignRepository.findAll();
    }

    private LocalDate parseDate(String fieldName, String value) {
        try {
            return hasText(value) ? LocalDate.parse(value.trim()) : null;
        } catch (Exception exception) {
            log.debug("Failed to parse {} from value '{}' as date: {}", fieldName, value,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsKeyword(Opportunity o, String keyword) {
        String kw = keyword.toLowerCase();
        return (o.getDealerName() != null && o.getDealerName().toLowerCase().contains(kw))
                || (o.getDealerCode() != null && o.getDealerCode().toLowerCase().contains(kw))
                || (o.getOpportunityId() != null && o.getOpportunityId().toLowerCase().contains(kw));
    }

    private boolean matchesExact(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && actual.equalsIgnoreCase(expected.trim());
    }

    private boolean matchesContains(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && actual.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    // ── Sorting helpers ────────────────────────────────

    private List<Target> sortTargets(List<Target> list, String sortBy, String sortOrder) {
        Comparator<Target> cmp = switch (sortBy != null ? sortBy : "dealerCode") {
            case "targetYear" -> Comparator.comparing(Target::getTargetYear);
            case "targetMonth" -> Comparator.comparing(Target::getTargetMonth);
            case "asKTarget" -> Comparator.comparing(Target::getAsKTarget);
            default -> Comparator.comparing(Target::getDealerCode);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Opportunity> sortOpportunities(List<Opportunity> list, String sortBy, String sortOrder) {
        Comparator<Opportunity> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "stageName" -> Comparator.comparing(Opportunity::getStageName);
            case "dealerCode" -> Comparator.comparing(Opportunity::getDealerCode);
            default -> Comparator.comparing(Opportunity::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Lead> sortLeads(List<Lead> list, String sortBy, String sortOrder) {
        Comparator<Lead> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "leadSource" -> Comparator.comparing(Lead::getLeadSource);
            case "dealerCode" -> Comparator.comparing(Lead::getDealerCode);
            default -> Comparator.comparing(Lead::getCreatedDate, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Task> sortTasks(List<Task> list, String sortBy, String sortOrder) {
        Comparator<Task> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "status" -> Comparator.comparing(Task::getStatus);
            case "dealerCode" -> Comparator.comparing(Task::getDealerCode);
            default -> Comparator.comparing(Task::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    private List<Campaign> sortCampaigns(List<Campaign> list, String sortBy, String sortOrder) {
        Comparator<Campaign> cmp = switch (sortBy != null ? sortBy : "createdDate") {
            case "campaignType" -> Comparator.comparing(Campaign::getCampaignType);
            case "dealerCode" -> Comparator.comparing(Campaign::getDealerCode);
            default -> Comparator.comparing(Campaign::getCreatedDate);
        };
        if (!"asc".equalsIgnoreCase(sortOrder)) cmp = cmp.reversed();
        return list.stream().sorted(cmp).toList();
    }

    // ── DTO mappers ────────────────────────────────────

    private TargetDetail toTargetDetail(Target t) {
        return new TargetDetail(t.getDealerCode(), t.getDealerName(), t.getCity(),
                t.getDealerGroupName(), t.getProductModel(), t.getTargetYear(), t.getTargetMonth(),
                t.getAsKTarget(), t.getOpportunityWonCount());
    }

    private OpportunityDetail toOpportunityDetail(Opportunity o) {
        return new OpportunityDetail(o.getOpportunityId(), o.getDealerCode(), o.getDealerName(),
                o.getCity(), o.getDealerGroupName(), o.getProductModel(), o.getStageName(),
                o.getLeadSource(), o.getCreatedDate().toString(), o.getProbability());
    }

    private LeadDetail toLeadDetail(Lead l) {
        return new LeadDetail(l.getLeadId(), l.getDealerCode(), l.getDealerName(), l.getCity(),
                l.getDealerGroupName(), l.getLeadSource(), l.getStageName(), l.getProductModel(),
                formatDate(l.getCreatedDate()), l.getConverted());
    }

    private TaskDetail toTaskDetail(Task t) {
        return new TaskDetail(t.getTaskId(), t.getDealerCode(), t.getDealerName(), t.getCity(),
                t.getDealerGroupName(), t.getOpportunityId(), t.getStatus(), t.getCreatedDate().toString());
    }

    private CampaignDetail toCampaignDetail(Campaign c) {
        return new CampaignDetail(c.getCampaignId(), c.getDealerCode(), c.getDealerName(), c.getCity(),
                c.getDealerGroupName(), c.getProductModel(), c.getCampaignType(),
                c.getCreatedDate().toString(), c.getActualOpportunityCount(), c.getTotalNewCustomerTarget());
    }

    private String formatDate(LocalDate value) {
        return value != null ? value.toString() : null;
    }
}
