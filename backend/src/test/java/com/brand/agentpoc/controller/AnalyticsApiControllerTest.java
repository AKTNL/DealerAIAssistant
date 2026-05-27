package com.brand.agentpoc.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.brand.agentpoc.dto.detail.TargetDetail;
import com.brand.agentpoc.dto.metrics.TargetMetrics;
import com.brand.agentpoc.dto.response.ApiPage;
import com.brand.agentpoc.dto.response.ApiResult;
import com.brand.agentpoc.service.AnalyticsApiService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalyticsApiControllerTest {

    private MockMvc mockMvc;
    private AnalyticsApiService analyticsApiService;

    @BeforeEach
    void setUp() {
        analyticsApiService = mock(AnalyticsApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsApiController(analyticsApiService))
                .build();
    }

    @Test
    void getTargetMetricsReturnsResultWrapper() throws Exception {
        when(analyticsApiService.getTargetMetrics(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ApiResult.success(
                        new TargetMetrics(5, 1000, 800, 80.0,
                                new TargetMetrics.DealerMetric("D001", "Low", 60.0),
                                new TargetMetrics.DealerMetric("D005", "High", 95.0))));

        mockMvc.perform(get("/api/targets/metrics?year=2026&month=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.totalDealers").value(5))
                .andExpect(jsonPath("$.data.averageAchievementRate").value(80.0))
                .andExpect(jsonPath("$.data.lowestDealer.dealerCode").value("D001"))
                .andExpect(jsonPath("$.data.highestDealer.dealerCode").value("D005"));
    }

    @Test
    void getTargetDetailsReturnsPaginationWrapper() throws Exception {
        when(analyticsApiService.getTargetDetails(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ApiResult.success(
                        ApiPage.of(List.of(
                                new TargetDetail("D001", "Store A", "Beijing", "Group1",
                                        "ModelX", 2026, 5, 100, 80)),
                                1, 1, 50)));

        mockMvc.perform(get("/api/targets/details?page=1&pageSize=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }
}
