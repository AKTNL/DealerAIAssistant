package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
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
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;
    private final DealerRepository dealerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final TargetRepository targetRepository;
    private final LeadRepository leadRepository;

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

        Resource resource = resourceLoader.getResource(appProperties.getExcel().getPath());
        if (resource.exists()) {
            log.info("Configured Excel detected at {}. Fallback sample data will be used until parser implementation is completed.",
                    appProperties.getExcel().getPath());
        } else {
            log.warn("Excel resource not found at {}. Seeding built-in sample data instead.",
                    appProperties.getExcel().getPath());
        }

        seedFallbackData();

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
                new com.brand.agentpoc.entity.Task("TSK-3001", "BJ001", "Beijing Star Motors", "Beijing",
                        "North Star Group", "OPP-1001", "Completed", LocalDate.of(2026, 4, 3)),
                new com.brand.agentpoc.entity.Task("TSK-3002", "BJ002", "Beijing Horizon Auto", "Beijing",
                        "North Star Group", "OPP-1002", "Pending", LocalDate.of(2026, 4, 7)),
                new com.brand.agentpoc.entity.Task("TSK-3003", "SH001", "Shanghai Prime Mobility", "Shanghai",
                        "East River Group", "OPP-1003", "Completed", LocalDate.of(2026, 4, 4)),
                new com.brand.agentpoc.entity.Task("TSK-3004", "HZ001", "Hangzhou Lakeside Auto", "Hangzhou",
                        "East River Group", "OPP-1004", "In Progress", LocalDate.of(2026, 4, 10)),
                new com.brand.agentpoc.entity.Task("TSK-3005", "GZ001", "Guangzhou Motion Hub", "Guangzhou",
                        "South Bay Group", "OPP-1005", "Pending", LocalDate.of(2026, 4, 12)),
                new com.brand.agentpoc.entity.Task("TSK-3006", "CD001", "Chengdu Drive Center", "Chengdu",
                        "West Link Group", "OPP-1006", "Overdue", LocalDate.of(2026, 4, 14))
        ));

        targetRepository.saveAll(List.of(
                new Target("BJ001", "Beijing Star Motors", "Beijing", "North Star Group", "M7", 2026, 4, 120, 92),
                new Target("BJ002", "Beijing Horizon Auto", "Beijing", "North Star Group", "M7", 2026, 4, 100, 68),
                new Target("SH001", "Shanghai Prime Mobility", "Shanghai", "East River Group", "X5", 2026, 4, 130, 126),
                new Target("HZ001", "Hangzhou Lakeside Auto", "Hangzhou", "East River Group", "X5", 2026, 4, 110, 97),
                new Target("GZ001", "Guangzhou Motion Hub", "Guangzhou", "South Bay Group", "E3", 2026, 4, 105, 88),
                new Target("CD001", "Chengdu Drive Center", "Chengdu", "West Link Group", "E3", 2026, 4, 95, 61)
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
}
