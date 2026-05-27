package com.brand.agentpoc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.dto.response.DataQueryResponse;
import com.brand.agentpoc.service.DataQueryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataQueryControllerTest {

    private DataQueryService dataQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dataQueryService = mock(DataQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DataQueryController(dataQueryService))
                .build();
    }

    @Test
    void validDatasetReturnsDataQueryResponse() throws Exception {
        when(dataQueryService.query(eq("dealers"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse(
                        "dealers",
                        Map.of("city", "Beijing"),
                        1,
                        List.of(Map.of("dealerCode", "D001")),
                        Map.of()
                ));

        mockMvc.perform(get("/api/v1/data/dealers?city=Beijing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("dealers"))
                .andExpect(jsonPath("$.filters.city").value("Beijing"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].dealerCode").value("D001"));
    }

    @Test
    void passesFiltersToService() throws Exception {
        when(dataQueryService.query(eq("opportunities"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new DataQueryResponse("opportunities", Map.of(), 0, List.of(), Map.of()));

        mockMvc.perform(get("/api/v1/data/opportunities?dealerCode=D001&stageName=Won"))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.captor();
        verify(dataQueryService).query(eq("opportunities"), filtersCaptor.capture());
        assertThat(filtersCaptor.getValue())
                .containsEntry("dealerCode", "D001")
                .containsEntry("stageName", "Won");
    }

    @Test
    void unsupportedDatasetRouteDoesNotReachService() throws Exception {
        mockMvc.perform(get("/api/v1/data/unknown"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(dataQueryService);
    }
}
