package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TenantImportIsolationTest {

    @Test
    void activeBatchesAndQualityStatusAreTenantLocal() {
        ImportBatchService batches = new ImportBatchService();
        ImportQualityService quality = new ImportQualityService();
        batches.activateTenantBatch(1L, "same-business-batch", "test", false, "tenant-a");
        batches.activateTenantBatch(2L, "same-business-batch", "test", false, "tenant-b");
        quality.publish(1L, status("tenant-a"));
        quality.publish(2L, status("tenant-b"));

        Dealer tenantA = new Dealer("D001", "Tenant A dealer", "A", "G", "same-business-batch", 1L);
        Dealer tenantB = new Dealer("D001", "Tenant B dealer", "B", "G", "same-business-batch", 2L);

        assertThat(batches.filterActive(List.of(tenantA, tenantB), 1L)).containsExactly(tenantA);
        assertThat(batches.filterActive(List.of(tenantA, tenantB), 2L)).containsExactly(tenantB);
        assertThat(quality.getLatest(1L).message()).isEqualTo("tenant-a");
        assertThat(quality.getLatest(2L).message()).isEqualTo("tenant-b");
    }

    @Test
    void mixedTenantParsedWorkbookFailsBeforeAnyRepositoryWrite() throws Exception {
        DealerRepository dealers = mock(DealerRepository.class);
        OpportunityRepository opportunities = mock(OpportunityRepository.class);
        CampaignRepository campaigns = mock(CampaignRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        TargetRepository targets = mock(TargetRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        ExcelImportService service = new ExcelImportService(
                new AppProperties(),
                new DefaultResourceLoader(),
                dealers,
                opportunities,
                campaigns,
                tasks,
                targets,
                leads
        );
        Object parsed = parsedWorkbook(List.of(
                new Dealer("D001", "Tenant A dealer", "A", "G", "batch", 1L),
                new Dealer("D002", "Tenant B dealer", "B", "G", "batch", 2L)
        ));

        Method persist = ExcelImportService.class.getDeclaredMethod(
                "persistParsedWorkbook", parsed.getClass(), Long.class);
        persist.setAccessible(true);

        assertThatThrownBy(() -> invoke(persist, service, parsed, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Imported rows must belong to the selected tenant.");
        verify(dealers, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(opportunities, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(campaigns, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(tasks, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(targets, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(leads, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    private Object parsedWorkbook(List<Dealer> dealers) throws Exception {
        Class<?> type = Class.forName("com.brand.agentpoc.service.ExcelImportService$ParsedWorkbook");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(dealers, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void invoke(Method method, Object target, Object... args) throws Throwable {
        try {
            method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private com.brand.agentpoc.dto.response.ImportDataStatus status(String message) {
        return new com.brand.agentpoc.dto.response.ImportDataStatus(
                "test", false, message,
                new com.brand.agentpoc.dto.response.ImportDataStatus.Batch(
                        "same-business-batch", true, "GLOBAL", null, Instant.now().toString()),
                new com.brand.agentpoc.dto.response.ImportDataStatus.Totals(0, 0, 0, 0),
                java.util.Map.of()
        );
    }
}
