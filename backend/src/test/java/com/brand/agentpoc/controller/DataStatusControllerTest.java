package com.brand.agentpoc.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.dto.response.ImportDataStatus;
import com.brand.agentpoc.service.ImportQualityService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataStatusControllerTest {

    @Test
    void returnsTheLatestImportStatus() throws Exception {
        ImportQualityService service = new ImportQualityService();
        service.publish(new ImportDataStatus(
                "built-in-sample",
                true,
                "Fallback data is active.",
                new ImportDataStatus.Totals(10, 8, 1, 2),
                Map.of()
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DataStatusController(service)).build();

        mockMvc.perform(get("/api/data-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.source").value("built-in-sample"))
                .andExpect(jsonPath("$.data.fallbackActive").value(true))
                .andExpect(jsonPath("$.data.totals.skippedRows").value(2));
    }
}
