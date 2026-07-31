package com.brand.agentpoc.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.dto.response.DashboardSummary;
import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.service.DashboardService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTest {

    private MockMvc mockMvc;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
    }

    @Test
    void getSummaryReturnsResultWrapper() throws Exception {
        when(dashboardService.getSummary()).thenReturn(summary());

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.overview.dealerCount").value(2))
                .andExpect(jsonPath("$.data.overview.targetAchievementRate").value(75.0))
                .andExpect(jsonPath("$.data.dataStatus.source").value("configured-workbook"));
    }

    private DashboardSummary summary() {
        return new DashboardSummary(
                new DashboardSummary.DataStatus(
                        "configured-workbook",
                        false,
                        true,
                        false,
                        "ok",
                        new ImportDataStatus.Batch("batch-1", true, "GLOBAL", null, "2026-07-31T00:00:00Z"),
                        10,
                        10,
                        0,
                        0,
                        List.of()
                ),
                new DashboardSummary.Overview(
                        2,
                        100,
                        75,
                        75,
                        10,
                        4,
                        8,
                        2,
                        7,
                        1,
                        3,
                        12,
                        75.0,
                        66.7,
                        25.0,
                        71.4,
                        14.3,
                        60.0
                ),
                new DashboardSummary.TargetAchievement(100, 75, List.of(), List.of()),
                new DashboardSummary.OpportunityFunnel(10, 4, 2, 4, 66.7, List.of()),
                new DashboardSummary.LeadSources(8, 2, 25.0, List.of()),
                new DashboardSummary.FollowUpTasks(7, 5, 1, 1, 71.4, 14.3, List.of()),
                new DashboardSummary.CampaignEffect(3, 12, 20, 12, 20, 60.0, List.of())
        );
    }
}
