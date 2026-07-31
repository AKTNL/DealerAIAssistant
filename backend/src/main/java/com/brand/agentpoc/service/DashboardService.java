package com.brand.agentpoc.service;

import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.dto.response.ImportDataStatus;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int PANEL_LIMIT = 5;
    private static final String CLOSED_WON = "Closed Won";
    private static final String CLOSED_LOST = "Closed Lost";
    private static final String COMPLETED = "Completed";
    private static final String OVERDUE = "Overdue";

    private final DealerRepository dealerRepository;
    private final TargetRepository targetRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final TaskRepository taskRepository;
    private final CampaignRepository campaignRepository;
    private final ImportBatchService importBatchService;
    private final ImportQualityService importQualityService;

    public DashboardService(
            DealerRepository dealerRepository,
            TargetRepository targetRepository,
            OpportunityRepository opportunityRepository,
            LeadRepository leadRepository,
            TaskRepository taskRepository,
            CampaignRepository campaignRepository,
            ImportBatchService importBatchService,
            ImportQualityService importQualityService
    ) {
        this.dealerRepository = dealerRepository;
        this.targetRepository = targetRepository;
        this.opportunityRepository = opportunityRepository;
        this.leadRepository = leadRepository;
        this.taskRepository = taskRepository;
        this.campaignRepository = campaignRepository;
        this.importBatchService = importBatchService;
        this.importQualityService = importQualityService;
    }

    public DashboardSummary getSummary() {
        List<Dealer> dealers = active(dealerRepository.findAll());
        List<Target> targets = active(targetRepository.findAll());
        List<Opportunity> opportunities = active(opportunityRepository.findAll());
        List<Lead> leads = active(leadRepository.findAll());
        List<com.brand.agentpoc.entity.Task> tasks = active(taskRepository.findAll());
        List<Campaign> campaigns = active(campaignRepository.findAll());

        return new DashboardSummary(
                dataStatus(),
                overview(dealers, targets, opportunities, leads, tasks, campaigns),
                targetAchievement(targets),
                opportunityFunnel(opportunities),
                leadSources(leads),
                followUpTasks(tasks),
                campaignEffect(campaigns)
        );
    }

    private DashboardSummary.DataStatus dataStatus() {
        ImportDataStatus status = importQualityService.getLatest();
        int issueCount = issueCount(status);
        int skippedRows = status.totals().skippedRows();
        return new DashboardSummary.DataStatus(
                status.source(),
                status.fallbackActive(),
                isSimulatedData(status),
                status.totals().importedRows() == 0 || skippedRows > 0 || issueCount > 0,
                status.message(),
                status.batch(),
                status.totals().processedRows(),
                status.totals().importedRows(),
                skippedRows,
                issueCount,
                issueSummaries(status)
        );
    }

    private DashboardSummary.Overview overview(
            List<Dealer> dealers,
            List<Target> targets,
            List<Opportunity> opportunities,
            List<Lead> leads,
            List<com.brand.agentpoc.entity.Task> tasks,
            List<Campaign> campaigns
    ) {
        TargetTotals targetTotals = targetTotals(targets);
        OpportunityTotals opportunityTotals = opportunityTotals(opportunities);
        LeadTotals leadTotals = leadTotals(leads);
        TaskTotals taskTotals = taskTotals(tasks);
        CampaignTotals campaignTotals = campaignTotals(campaigns);

        return new DashboardSummary.Overview(
                dealers.size(),
                targetTotals.target(),
                targetTotals.observedWon(),
                targetTotals.comparableWon(),
                opportunities.size(),
                opportunityTotals.won(),
                leads.size(),
                leadTotals.converted(),
                tasks.size(),
                taskTotals.overdue(),
                campaigns.size(),
                campaignTotals.actual(),
                percentage(targetTotals.comparableWon(), targetTotals.target()),
                percentage(opportunityTotals.won(), opportunityTotals.closed()),
                percentage(leadTotals.converted(), leads.size()),
                percentage(taskTotals.completed(), tasks.size()),
                percentage(taskTotals.overdue(), tasks.size()),
                percentage(campaignTotals.comparableActual(), campaignTotals.comparableTarget())
        );
    }

    private DashboardSummary.TargetAchievement targetAchievement(List<Target> targets) {
        TargetTotals totals = targetTotals(targets);
        List<DashboardSummary.DealerAchievement> lowDealers = aggregateTargetRates(
                targets,
                Target::getDealerCode,
                this::dealerAchievement
        ).stream()
                .sorted(Comparator.comparingDouble(DashboardSummary.DealerAchievement::achievementRate))
                .limit(PANEL_LIMIT)
                .toList();
        List<DashboardSummary.RegionAchievement> regions = aggregateTargetRates(
                targets,
                Target::getCity,
                this::regionAchievement
        ).stream()
                .sorted(Comparator.comparingDouble(DashboardSummary.RegionAchievement::achievementRate))
                .limit(PANEL_LIMIT)
                .toList();
        return new DashboardSummary.TargetAchievement(
                totals.target(),
                totals.comparableWon(),
                lowDealers,
                regions
        );
    }

    private DashboardSummary.OpportunityFunnel opportunityFunnel(List<Opportunity> opportunities) {
        OpportunityTotals totals = opportunityTotals(opportunities);
        List<DashboardSummary.DistributionMetric> stages = distribution(
                opportunities,
                Opportunity::getStageName,
                opportunities.size()
        );
        return new DashboardSummary.OpportunityFunnel(
                opportunities.size(),
                totals.won(),
                totals.lost(),
                opportunities.size() - totals.closed(),
                percentage(totals.won(), totals.closed()),
                stages
        );
    }

    private DashboardSummary.LeadSources leadSources(List<Lead> leads) {
        LeadTotals totals = leadTotals(leads);
        List<DashboardSummary.LeadSourceMetric> sources = leads.stream()
                .collect(Collectors.groupingBy(Lead::getLeadSource, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> leadSourceMetric(entry.getKey(), entry.getValue(), leads.size()))
                .sorted(Comparator.comparingDouble(DashboardSummary.LeadSourceMetric::conversionRate)
                        .thenComparing(Comparator.comparingLong(DashboardSummary.LeadSourceMetric::totalCount).reversed()))
                .limit(PANEL_LIMIT)
                .toList();
        return new DashboardSummary.LeadSources(
                leads.size(),
                totals.converted(),
                percentage(totals.converted(), leads.size()),
                sources
        );
    }

    private DashboardSummary.FollowUpTasks followUpTasks(List<com.brand.agentpoc.entity.Task> tasks) {
        TaskTotals totals = taskTotals(tasks);
        List<DashboardSummary.TaskBacklog> backlogDealers = tasks.stream()
                .filter(task -> !isStatus(task.getStatus(), COMPLETED))
                .collect(Collectors.groupingBy(com.brand.agentpoc.entity.Task::getDealerCode, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> taskBacklog(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(DashboardSummary.TaskBacklog::totalBacklog).reversed())
                .limit(PANEL_LIMIT)
                .toList();
        return new DashboardSummary.FollowUpTasks(
                tasks.size(),
                totals.completed(),
                tasks.size() - totals.completed() - totals.overdue(),
                totals.overdue(),
                percentage(totals.completed(), tasks.size()),
                percentage(totals.overdue(), tasks.size()),
                backlogDealers
        );
    }

    private DashboardSummary.CampaignEffect campaignEffect(List<Campaign> campaigns) {
        CampaignTotals totals = campaignTotals(campaigns);
        List<DashboardSummary.CampaignOutcome> lowPerformingCampaigns = campaigns.stream()
                .filter(this::hasComparableCampaignOpportunityTarget)
                .map(this::campaignOutcome)
                .sorted(Comparator.comparingDouble(DashboardSummary.CampaignOutcome::attainmentRate))
                .limit(PANEL_LIMIT)
                .toList();
        return new DashboardSummary.CampaignEffect(
                campaigns.size(),
                totals.actual(),
                totals.target(),
                totals.comparableActual(),
                totals.comparableTarget(),
                percentage(totals.comparableActual(), totals.comparableTarget()),
                lowPerformingCampaigns
        );
    }

    private <T> List<T> aggregateTargetRates(
            List<Target> targets,
            Function<Target, String> classifier,
            TargetRateMapper<T> mapper
    ) {
        return targets.stream()
                .filter(target -> target.getAsKTarget() != null)
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> mapper.toMetric(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    private DashboardSummary.DealerAchievement dealerAchievement(String dealerCode, List<Target> rows) {
        int target = sumTarget(rows);
        if (target <= 0) {
            return null;
        }
        int won = sumWon(rows);
        Target first = rows.getFirst();
        return new DashboardSummary.DealerAchievement(
                dealerCode,
                first.getDealerName(),
                first.getCity(),
                percentage(won, target),
                won,
                target
        );
    }

    private DashboardSummary.RegionAchievement regionAchievement(String region, List<Target> rows) {
        int target = sumTarget(rows);
        if (target <= 0) {
            return null;
        }
        int won = sumWon(rows);
        return new DashboardSummary.RegionAchievement(region, percentage(won, target), won, target);
    }

    private List<DashboardSummary.DistributionMetric> distribution(
            List<Opportunity> rows,
            Function<Opportunity, String> classifier,
            int total
    ) {
        return rows.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(PANEL_LIMIT)
                .map(entry -> new DashboardSummary.DistributionMetric(
                        entry.getKey(),
                        entry.getValue(),
                        percentage(entry.getValue(), total)
                ))
                .toList();
    }

    private DashboardSummary.LeadSourceMetric leadSourceMetric(String source, List<Lead> rows, int totalLeads) {
        long converted = rows.stream().filter(lead -> Boolean.TRUE.equals(lead.getConverted())).count();
        return new DashboardSummary.LeadSourceMetric(
                source,
                rows.size(),
                converted,
                percentage(converted, rows.size()),
                percentage(rows.size(), totalLeads)
        );
    }

    private DashboardSummary.TaskBacklog taskBacklog(String dealerCode, List<com.brand.agentpoc.entity.Task> rows) {
        int overdue = (int) rows.stream().filter(task -> isStatus(task.getStatus(), OVERDUE)).count();
        int open = rows.size() - overdue;
        return new DashboardSummary.TaskBacklog(
                dealerCode,
                rows.getFirst().getDealerName(),
                open,
                overdue,
                rows.size()
        );
    }

    private DashboardSummary.CampaignOutcome campaignOutcome(Campaign campaign) {
        return new DashboardSummary.CampaignOutcome(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getDealerName(),
                percentage(campaign.getActualOpportunityCount(), campaign.getTotalNewCustomerTarget()),
                defaultZero(campaign.getActualOpportunityCount()),
                defaultZero(campaign.getTotalNewCustomerTarget())
        );
    }

    private TargetTotals targetTotals(List<Target> targets) {
        List<Target> comparable = targets.stream()
                .filter(target -> target.getAsKTarget() != null)
                .toList();
        return new TargetTotals(sumTarget(comparable), sumWon(comparable), sumWon(targets));
    }

    private OpportunityTotals opportunityTotals(List<Opportunity> opportunities) {
        int won = (int) opportunities.stream()
                .filter(opportunity -> isStatus(opportunity.getStageName(), CLOSED_WON))
                .count();
        int lost = (int) opportunities.stream()
                .filter(opportunity -> isStatus(opportunity.getStageName(), CLOSED_LOST))
                .count();
        return new OpportunityTotals(won, lost);
    }

    private LeadTotals leadTotals(List<Lead> leads) {
        int converted = (int) leads.stream().filter(lead -> Boolean.TRUE.equals(lead.getConverted())).count();
        return new LeadTotals(converted);
    }

    private TaskTotals taskTotals(List<com.brand.agentpoc.entity.Task> tasks) {
        int completed = (int) tasks.stream().filter(task -> isStatus(task.getStatus(), COMPLETED)).count();
        int overdue = (int) tasks.stream().filter(task -> isStatus(task.getStatus(), OVERDUE)).count();
        return new TaskTotals(completed, overdue);
    }

    private CampaignTotals campaignTotals(List<Campaign> campaigns) {
        int target = campaigns.stream()
                .map(Campaign::getTotalNewCustomerTarget)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int actual = campaigns.stream()
                .map(Campaign::getActualOpportunityCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        List<Campaign> comparable = campaigns.stream()
                .filter(this::hasComparableCampaignOpportunityTarget)
                .toList();
        int comparableTarget = comparable.stream().mapToInt(Campaign::getTotalNewCustomerTarget).sum();
        int comparableActual = comparable.stream().mapToInt(Campaign::getActualOpportunityCount).sum();
        return new CampaignTotals(target, actual, comparableTarget, comparableActual);
    }

    private boolean hasComparableCampaignOpportunityTarget(Campaign campaign) {
        return campaign.getTotalNewCustomerTarget() != null
                && campaign.getActualOpportunityCount() != null
                && campaign.getTotalNewCustomerTarget() > 0;
    }

    private int issueCount(ImportDataStatus status) {
        return status.sheets().values().stream()
                .flatMap(sheet -> sheet.issues().values().stream())
                .mapToInt(Integer::intValue)
                .sum();
    }

    private List<String> issueSummaries(ImportDataStatus status) {
        return status.sheets().entrySet()
                .stream()
                .flatMap(sheetEntry -> sheetEntry.getValue().issues().entrySet().stream()
                        .map(issue -> "%s:%s=%d".formatted(sheetEntry.getKey(), issue.getKey(), issue.getValue())))
                .limit(PANEL_LIMIT)
                .toList();
    }

    private boolean isSimulatedData(ImportDataStatus status) {
        return status.fallbackActive()
                || "built-in-sample".equals(status.source())
                || "configured-workbook".equals(status.source());
    }

    private int sumTarget(List<Target> rows) {
        return rows.stream()
                .map(Target::getAsKTarget)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int sumWon(List<Target> rows) {
        return rows.stream().mapToInt(Target::getOpportunityWonCount).sum();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((double) numerator / denominator * 1000.0) / 10.0;
    }

    private boolean isStatus(String actual, String expected) {
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private <T extends com.brand.agentpoc.entity.BatchScoped> List<T> active(List<T> rows) {
        return importBatchService.filterActive(rows);
    }

    @FunctionalInterface
    private interface TargetRateMapper<T> {
        T toMetric(String label, List<Target> rows);
    }

    private record TargetTotals(int target, int comparableWon, int observedWon) {
    }

    private record OpportunityTotals(int won, int lost) {
        int closed() {
            return won + lost;
        }
    }

    private record LeadTotals(int converted) {
    }

    private record TaskTotals(int completed, int overdue) {
    }

    private record CampaignTotals(int target, int actual, int comparableTarget, int comparableActual) {
    }
}
