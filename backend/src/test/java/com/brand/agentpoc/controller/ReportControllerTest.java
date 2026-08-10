package com.brand.agentpoc.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.reporting.application.ReportService;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ReportControllerTest {

    private ReportService reportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportService = org.mockito.Mockito.mock(ReportService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportService))
                .setValidator(validator)
                .build();
    }

    @Test
    void createsAndExportsMarkdownDrafts() throws Exception {
        ReportDraft draft = draft();
        when(reportService.generate(any())).thenReturn(draft);
        when(reportService.require("report-1")).thenReturn(draft);

        mockMvc.perform(post("/api/reports/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"daily\",\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("report-1"));

        mockMvc.perform(get("/api/reports/drafts/report-1/markdown"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("report-daily-report-1.md")))
                .andExpect(content().string("# Dealer Operations Daily Report"));
    }

    @Test
    void listsAndGetsDraftsInsideTheStandardResponseEnvelope() throws Exception {
        ReportDraft draft = draft();
        when(reportService.list()).thenReturn(List.of(draft));
        when(reportService.require("report-1")).thenReturn(draft);

        mockMvc.perform(get("/api/reports/drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("report-1"));

        mockMvc.perform(get("/api/reports/drafts/report-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("report-1"));
    }

    @Test
    void returnsNotFoundWhenDraftDoesNotExist() throws Exception {
        when(reportService.require("missing")).thenThrow(new NoSuchElementException("Report draft was not found."));

        mockMvc.perform(get("/api/reports/drafts/missing/markdown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void rejectsMissingRequiredRequestFields() throws Exception {
        mockMvc.perform(post("/api/reports/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"daily\"}"))
                .andExpect(status().isBadRequest());
    }

    private ReportDraft draft() {
        return new ReportDraft(
                "report-1", ReportType.DAILY, "Dealer Operations Daily Report", "en",
                "# Dealer Operations Daily Report", Instant.parse("2026-08-10T05:00:00Z"),
                "batch-1", ReportScope.global(), "deterministic", "reporting-v1"
        );
    }
}
