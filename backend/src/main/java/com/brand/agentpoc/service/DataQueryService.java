package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Lead;
import com.brand.agentpoc.entity.Opportunity;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import com.brand.agentpoc.entity.Task;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataQueryService {

    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;

    public DataQueryService(
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

    @Transactional(readOnly = true)
    public DataQueryResponse query(String dataset, Map<String, String> filters) {
        Map<String, String> normalizedFilters = Map.copyOf(filters);

        return switch (dataset) {
            case "dealers" -> queryDealers(normalizedFilters);
            case "opportunities" -> queryOpportunities(normalizedFilters);
            case "campaigns" -> queryCampaigns(normalizedFilters);
            case "tasks" -> queryTasks(normalizedFilters);
            case "targets" -> queryTargets(normalizedFilters);
            case "leads" -> queryLeads(normalizedFilters);
            default -> throw new IllegalArgumentException("Unsupported dataset: " + dataset);
        };
    }

    private DataQueryResponse queryDealers(Map<String, String> filters) {
        String keyword = normalize(filters.get("keyword"));

        List<Map<String, Object>> items = dealerRepository.findAll().stream()
                .filter(dealer -> keyword == null
                        || contains(dealer.getDealerCode(), keyword)
                        || contains(dealer.getDealerName(), keyword)
                        || contains(dealer.getCity(), keyword)
                        || contains(dealer.getDealerGroupName(), keyword))
                .filter(dealer -> matchesExact(dealer.getDealerCode(), filters.get("dealerCode")))
                .filter(dealer -> matchesExact(dealer.getCity(), filters.get("city")))
                .filter(dealer -> matchesExact(dealer.getDealerGroupName(), filters.get("dealerGroupName")))
                .sorted(Comparator.comparing(Dealer::getDealerCode))
                .map(this::toDealerMap)
                .toList();

        return new DataQueryResponse("dealers", filters, items.size(), items);
    }

    private DataQueryResponse queryOpportunities(Map<String, String> filters) {
        LocalDate startDate = parseDate(filters.get("startDate"));
        LocalDate endDate = parseDate(filters.get("endDate"));

        List<Map<String, Object>> items = opportunityRepository.findAll().stream()
                .filter(opportunity -> matchesExact(opportunity.getDealerCode(), filters.get("dealerCode")))
                .filter(opportunity -> matchesExact(opportunity.getCity(), filters.get("city")))
                .filter(opportunity -> matchesExact(opportunity.getDealerGroupName(), filters.get("dealerGroupName")))
                .filter(opportunity -> matchesExact(opportunity.getProductModel(), filters.get("productModel")))
                .filter(opportunity -> matchesExact(opportunity.getStageName(), filters.get("stageName")))
                .filter(opportunity -> matchesExact(opportunity.getLeadSource(), filters.get("leadSource")))
                .filter(opportunity -> withinDateRange(opportunity.getCreatedDate(), startDate, endDate))
                .sorted(Comparator.comparing(Opportunity::getCreatedDate).reversed()
                        .thenComparing(Opportunity::getOpportunityId))
                .map(this::toOpportunityMap)
                .toList();

        return new DataQueryResponse("opportunities", filters, items.size(), items);
    }

    private DataQueryResponse queryCampaigns(Map<String, String> filters) {
        LocalDate startDate = parseDate(filters.get("startDate"));
        LocalDate endDate = parseDate(filters.get("endDate"));

        List<Map<String, Object>> items = campaignRepository.findAll().stream()
                .filter(campaign -> matchesExact(campaign.getDealerCode(), filters.get("dealerCode")))
                .filter(campaign -> matchesExact(campaign.getCity(), filters.get("city")))
                .filter(campaign -> matchesExact(campaign.getDealerGroupName(), filters.get("dealerGroupName")))
                .filter(campaign -> matchesExact(campaign.getProductModel(), filters.get("productModel")))
                .filter(campaign -> matchesExact(campaign.getCampaignType(), filters.get("campaignType")))
                .filter(campaign -> withinDateRange(campaign.getCreatedDate(), startDate, endDate))
                .sorted(Comparator.comparing(Campaign::getCreatedDate).reversed()
                        .thenComparing(Campaign::getCampaignId))
                .map(this::toCampaignMap)
                .toList();

        return new DataQueryResponse("campaigns", filters, items.size(), items);
    }

    private DataQueryResponse queryTasks(Map<String, String> filters) {
        LocalDate startDate = parseDate(filters.get("startDate"));
        LocalDate endDate = parseDate(filters.get("endDate"));

        List<Map<String, Object>> items = taskRepository.findAll().stream()
                .filter(task -> matchesExact(task.getDealerCode(), filters.get("dealerCode")))
                .filter(task -> matchesExact(task.getCity(), filters.get("city")))
                .filter(task -> matchesExact(task.getDealerGroupName(), filters.get("dealerGroupName")))
                .filter(task -> matchesExact(task.getOpportunityId(), filters.get("opportunityId")))
                .filter(task -> matchesExact(task.getStatus(), filters.get("status")))
                .filter(task -> withinDateRange(task.getCreatedDate(), startDate, endDate))
                .sorted(Comparator.comparing(Task::getCreatedDate).reversed()
                        .thenComparing(Task::getTaskId))
                .map(this::toTaskMap)
                .toList();

        return new DataQueryResponse("tasks", filters, items.size(), items);
    }

    private DataQueryResponse queryTargets(Map<String, String> filters) {
        Integer targetYear = parseInteger(filters.get("targetYear"));
        Integer targetMonth = parseInteger(filters.get("targetMonth"));

        List<Map<String, Object>> items = targetRepository.findAll().stream()
                .filter(target -> matchesExact(target.getDealerCode(), filters.get("dealerCode")))
                .filter(target -> matchesExact(target.getCity(), filters.get("city")))
                .filter(target -> matchesExact(target.getDealerGroupName(), filters.get("dealerGroupName")))
                .filter(target -> matchesExact(target.getProductModel(), filters.get("productModel")))
                .filter(target -> targetYear == null || Objects.equals(target.getTargetYear(), targetYear))
                .filter(target -> targetMonth == null || Objects.equals(target.getTargetMonth(), targetMonth))
                .sorted(Comparator.comparing(Target::getTargetYear).reversed()
                        .thenComparing(Target::getTargetMonth, Comparator.reverseOrder())
                        .thenComparing(Target::getDealerCode))
                .map(this::toTargetMap)
                .toList();

        return new DataQueryResponse("targets", filters, items.size(), items);
    }

    private DataQueryResponse queryLeads(Map<String, String> filters) {
        LocalDate startDate = parseDate(filters.get("startDate"));
        LocalDate endDate = parseDate(filters.get("endDate"));
        Boolean converted = parseBoolean(filters.get("isConverted"));

        List<Map<String, Object>> items = leadRepository.findAll().stream()
                .filter(lead -> matchesExact(lead.getDealerCode(), filters.get("dealerCode")))
                .filter(lead -> matchesExact(lead.getCity(), filters.get("city")))
                .filter(lead -> matchesExact(lead.getDealerGroupName(), filters.get("dealerGroupName")))
                .filter(lead -> matchesExact(lead.getLeadSource(), filters.get("leadSource")))
                .filter(lead -> matchesExact(lead.getStageName(), filters.get("stageName")))
                .filter(lead -> matchesExact(lead.getProductModel(), filters.get("productModel")))
                .filter(lead -> converted == null || Objects.equals(lead.getConverted(), converted))
                .filter(lead -> withinDateRange(lead.getCreatedDate(), startDate, endDate))
                .sorted(Comparator.comparing(Lead::getCreatedDate).reversed()
                        .thenComparing(Lead::getLeadId))
                .map(this::toLeadMap)
                .toList();

        return new DataQueryResponse("leads", filters, items.size(), items);
    }

    private Map<String, Object> toDealerMap(Dealer dealer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dealerCode", dealer.getDealerCode());
        item.put("dealerName", dealer.getDealerName());
        item.put("city", dealer.getCity());
        item.put("dealerGroupName", dealer.getDealerGroupName());
        return item;
    }

    private Map<String, Object> toOpportunityMap(Opportunity opportunity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("opportunityId", opportunity.getOpportunityId());
        item.put("dealerCode", opportunity.getDealerCode());
        item.put("dealerName", opportunity.getDealerName());
        item.put("city", opportunity.getCity());
        item.put("dealerGroupName", opportunity.getDealerGroupName());
        item.put("productModel", opportunity.getProductModel());
        item.put("stageName", opportunity.getStageName());
        item.put("leadSource", opportunity.getLeadSource());
        item.put("createdDate", opportunity.getCreatedDate().toString());
        item.put("expectedCloseDate", opportunity.getExpectedCloseDate().toString());
        item.put("probability", opportunity.getProbability());
        return item;
    }

    private Map<String, Object> toCampaignMap(Campaign campaign) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("campaignId", campaign.getCampaignId());
        item.put("dealerCode", campaign.getDealerCode());
        item.put("dealerName", campaign.getDealerName());
        item.put("city", campaign.getCity());
        item.put("dealerGroupName", campaign.getDealerGroupName());
        item.put("productModel", campaign.getProductModel());
        item.put("campaignType", campaign.getCampaignType());
        item.put("createdDate", campaign.getCreatedDate().toString());
        item.put("actualOpportunityCount", campaign.getActualOpportunityCount());
        item.put("totalNewCustomerTarget", campaign.getTotalNewCustomerTarget());
        return item;
    }

    private Map<String, Object> toTaskMap(Task task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getTaskId());
        item.put("dealerCode", task.getDealerCode());
        item.put("dealerName", task.getDealerName());
        item.put("city", task.getCity());
        item.put("dealerGroupName", task.getDealerGroupName());
        item.put("opportunityId", task.getOpportunityId());
        item.put("status", task.getStatus());
        item.put("createdDate", task.getCreatedDate().toString());
        return item;
    }

    private Map<String, Object> toTargetMap(Target target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dealerCode", target.getDealerCode());
        item.put("dealerName", target.getDealerName());
        item.put("city", target.getCity());
        item.put("dealerGroupName", target.getDealerGroupName());
        item.put("productModel", target.getProductModel());
        item.put("targetYear", target.getTargetYear());
        item.put("targetMonth", target.getTargetMonth());
        item.put("asKTarget", target.getAsKTarget());
        item.put("opportunityWonCount", target.getOpportunityWonCount());
        return item;
    }

    private Map<String, Object> toLeadMap(Lead lead) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("leadId", lead.getLeadId());
        item.put("dealerCode", lead.getDealerCode());
        item.put("dealerName", lead.getDealerName());
        item.put("city", lead.getCity());
        item.put("dealerGroupName", lead.getDealerGroupName());
        item.put("leadSource", lead.getLeadSource());
        item.put("stageName", lead.getStageName());
        item.put("productModel", lead.getProductModel());
        item.put("createdDate", lead.getCreatedDate().toString());
        item.put("isConverted", lead.getConverted());
        return item;
    }

    private boolean matchesExact(String actual, String filter) {
        String normalizedFilter = normalize(filter);
        String normalizedActual = normalize(actual);
        return normalizedFilter == null || (normalizedActual != null && normalizedActual.equalsIgnoreCase(normalizedFilter));
    }

    private boolean contains(String actual, String keyword) {
        String normalizedActual = normalize(actual);
        return normalizedActual != null && normalizedActual.contains(normalize(keyword));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private boolean withinDateRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return (startDate == null || !value.isBefore(startDate))
                && (endDate == null || !value.isAfter(endDate));
    }

    private LocalDate parseDate(String value) {
        try {
            return normalize(value) == null ? null : LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return normalize(value) == null ? null : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        return switch (normalized) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> null;
        };
    }
}
