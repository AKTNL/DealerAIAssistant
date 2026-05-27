package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.entity.Campaign;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.entity.Target;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import com.brand.agentpoc.test.LogCapture;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.DefaultResourceLoader;

class ExcelImportServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void importsTargetRowsWithBlankAskTargetAsZero() throws Exception {
        TargetRepository targetRepository = mock(TargetRepository.class);
        ExcelImportService service = importService(targetRepository, workbookWithTargetRows(List.<Object[]>of(
                targetRow("经销商AG(济南)", 7033, null, 2, "Aurora S", 2024, 1, "经销商集团O")
        )));

        service.run(mock(ApplicationArguments.class));

        List<Target> savedTargets = captureSavedTargets(targetRepository);
        assertThat(savedTargets).hasSize(1);
        assertThat(savedTargets.getFirst().getAsKTarget()).isZero();
        assertThat(savedTargets.getFirst().getOpportunityWonCount()).isEqualTo(2);
    }

    @Test
    void derivesCityFromParenthesizedChineseDealerName() throws Exception {
        TargetRepository targetRepository = mock(TargetRepository.class);
        ExcelImportService service = importService(targetRepository, workbookWithTargetRows(List.<Object[]>of(
                targetRow("经销商AJ(沈阳)", 7036, 10, 8, "Aurora S", 2026, 5, "经销商集团P")
        )));

        service.run(mock(ApplicationArguments.class));

        List<Target> savedTargets = captureSavedTargets(targetRepository);
        assertThat(savedTargets).hasSize(1);
        assertThat(savedTargets.getFirst().getCity()).isEqualTo("沈阳");
    }

    @Test
    void derivesDealersEvenWhenDealerGroupNameIsBlank() throws Exception {
        DealerRepository dealerRepository = mock(DealerRepository.class);
        TargetRepository targetRepository = mock(TargetRepository.class);
        ExcelImportService service = importService(targetRepository, dealerRepository, workbookWithTargetRows(List.<Object[]>of(
                targetRow("经销商AL(海口)", 7038, 10, 8, "Aurora S", 2026, 5, null)
        )));

        service.run(mock(ApplicationArguments.class));

        List<Dealer> savedDealers = captureSavedDealers(dealerRepository);
        assertThat(savedDealers).hasSize(1);
        assertThat(savedDealers.getFirst().getDealerCode()).isEqualTo("7038");
        assertThat(savedDealers.getFirst().getCity()).isEqualTo("海口");
        assertThat(savedDealers.getFirst().getDealerGroupName()).isEmpty();
    }

    @Test
    void importsCampaignRowsWithBlankNonCriticalFieldsUsingDefaults() throws Exception {
        TargetRepository targetRepository = mock(TargetRepository.class);
        DealerRepository dealerRepository = mock(DealerRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        ExcelImportService service = importService(
                targetRepository,
                dealerRepository,
                campaignRepository,
                workbookWithCampaignRows(List.<Object[]>of(new Object[]{
                        "CAM-001", null, null, null, null, "2026-05-12", null, null
                }))
        );

        service.run(mock(ApplicationArguments.class));

        List<Campaign> savedCampaigns = captureSavedCampaigns(campaignRepository);
        assertThat(savedCampaigns).hasSize(1);
        Campaign campaign = savedCampaigns.getFirst();
        assertThat(campaign.getCampaignId()).isEqualTo("CAM-001");
        assertThat(campaign.getCampaignType()).isEqualTo("0");
        assertThat(campaign.getDealerCode()).isEqualTo("未分配");
        assertThat(campaign.getDealerName()).isEqualTo("未分配");
        assertThat(campaign.getProductModel()).isEqualTo("未知");
        assertThat(campaign.getActualOpportunityCount()).isZero();
        assertThat(campaign.getTotalNewCustomerTarget()).isZero();
    }

    @Test
    void importsCampaignRowsWithInvalidNumericValuesLogsConversionDetails() throws Exception {
        TargetRepository targetRepository = mock(TargetRepository.class);
        DealerRepository dealerRepository = mock(DealerRepository.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        ExcelImportService service = importService(
                targetRepository,
                dealerRepository,
                campaignRepository,
                workbookWithCampaignRows(List.<Object[]>of(new Object[]{
                        "CAM-002", "D001", "Dealer A", "Model X", "Roadshow", "2026-05-12",
                        "not-a-number", 10
                }))
        );

        try (LogCapture logs = LogCapture.attach(ExcelImportService.class)) {
            service.run(mock(ApplicationArguments.class));

            List<Campaign> savedCampaigns = captureSavedCampaigns(campaignRepository);
            assertThat(savedCampaigns).hasSize(1);
            assertThat(savedCampaigns.getFirst().getActualOpportunityCount()).isZero();
            assertThat(logs.messages()).anySatisfy(message -> assertThat(message)
                    .contains("actualOpportunityCount", "not-a-number", "NumberFormatException"));
        }
    }

    private ExcelImportService importService(TargetRepository targetRepository, Path workbookPath) {
        return importService(targetRepository, mock(DealerRepository.class), workbookPath);
    }

    private ExcelImportService importService(
            TargetRepository targetRepository,
            DealerRepository dealerRepository,
            Path workbookPath
    ) {
        return importService(targetRepository, dealerRepository, mock(CampaignRepository.class), workbookPath);
    }

    private ExcelImportService importService(
            TargetRepository targetRepository,
            DealerRepository dealerRepository,
            CampaignRepository campaignRepository,
            Path workbookPath
    ) {
        AppProperties properties = new AppProperties();
        properties.getExcel().setPath(workbookPath.toString());

        OpportunityRepository opportunityRepository = mock(OpportunityRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        LeadRepository leadRepository = mock(LeadRepository.class);

        when(dealerRepository.count()).thenReturn(0L);
        when(opportunityRepository.count()).thenReturn(0L);
        when(campaignRepository.count()).thenReturn(0L);
        when(taskRepository.count()).thenReturn(0L);
        when(targetRepository.count()).thenReturn(0L);
        when(leadRepository.count()).thenReturn(0L);

        return new ExcelImportService(
                properties,
                new DefaultResourceLoader(),
                dealerRepository,
                opportunityRepository,
                campaignRepository,
                taskRepository,
                targetRepository,
                leadRepository
        );
    }

    private Path workbookWithCampaignRows(List<Object[]> campaignRows) throws Exception {
        Path workbookPath = tempDir.resolve("campaigns.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Campaign");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Id",
                    "Retailer__r.DealerCode__c",
                    "Retailer__r.Name",
                    "ProductModel__c",
                    "CampaignType__c",
                    "CreatedDate",
                    "NumberOfOpportunities",
                    "NewCustomerCount__c"
            };
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }

            for (int i = 0; i < campaignRows.size(); i++) {
                Object[] values = campaignRows.get(i);
                Row row = sheet.createRow(i + 1);
                for (int column = 0; column < values.length; column++) {
                    Object value = values[column];
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(column).setCellValue(String.valueOf(value));
                    }
                }
            }

            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }
        return workbookPath;
    }

    private Path workbookWithTargetRows(List<Object[]> targetRows) throws Exception {
        Path workbookPath = tempDir.resolve("sample.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("AE Target Data");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Retailer__r.Name",
                    "Retailer__r.DealerCode__c",
                    "Name",
                    "AaKTarget__c",
                    "OpportunityCreateCount__c",
                    "OpportunityWonCount__c",
                    "Opportunity_Type__c",
                    "ProductModel__c",
                    "TargetDate__c",
                    "Month__c",
                    "Year__c",
                    "DealerGroupName__c"
            };
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }

            for (int i = 0; i < targetRows.size(); i++) {
                Object[] values = targetRows.get(i);
                Row row = sheet.createRow(i + 1);
                for (int column = 0; column < values.length; column++) {
                    Object value = values[column];
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(column).setCellValue(String.valueOf(value));
                    }
                }
            }

            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        }
        return workbookPath;
    }

    private Object[] targetRow(
            String dealerName,
            Integer dealerCode,
            Integer asKTarget,
            Integer opportunityWonCount,
            String productModel,
            Integer targetYear,
            Integer targetMonth,
            String dealerGroupName
    ) {
        return new Object[]{
                dealerName,
                dealerCode,
                dealerCode + "-NewVehicle-" + productModel.replace(" ", "") + "-" + targetYear + "-" + targetMonth,
                asKTarget,
                0,
                opportunityWonCount,
                "NewVehicle",
                productModel,
                targetMonth + "/1/" + targetYear,
                targetMonth,
                targetYear,
                dealerGroupName
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Target> captureSavedTargets(TargetRepository targetRepository) {
        ArgumentCaptor<Iterable<Target>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(targetRepository).saveAll(captor.capture());
        List<Target> savedTargets = new ArrayList<>();
        captor.getValue().forEach(savedTargets::add);
        return savedTargets;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Dealer> captureSavedDealers(DealerRepository dealerRepository) {
        ArgumentCaptor<Iterable<Dealer>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(dealerRepository).saveAll(captor.capture());
        List<Dealer> savedDealers = new ArrayList<>();
        captor.getValue().forEach(savedDealers::add);
        return savedDealers;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Campaign> captureSavedCampaigns(CampaignRepository campaignRepository) {
        ArgumentCaptor<Iterable<Campaign>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(campaignRepository).saveAll(captor.capture());
        List<Campaign> savedCampaigns = new ArrayList<>();
        captor.getValue().forEach(savedCampaigns::add);
        return savedCampaigns;
    }
}
