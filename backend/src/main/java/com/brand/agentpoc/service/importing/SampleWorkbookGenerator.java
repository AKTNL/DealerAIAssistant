package com.brand.agentpoc.service.importing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

public final class SampleWorkbookGenerator {

    private static final int DEALER_COUNT = 80;
    private static final int TARGET_MODEL_COUNT = 4;
    private static final int OPPORTUNITY_COUNT = 30_000;
    private static final int LEAD_COUNT = 25_000;
    private static final int TASK_COUNT = 60_000;
    private static final int CAMPAIGN_COUNT = 2_400;
    private static final int WINDOW_SIZE = 500;
    private static final long RANDOM_SEED = 20260729L;
    private static final LocalDate START_MONTH = LocalDate.of(2025, 7, 1);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DEFAULT_OUTPUT = "../mockservice/SampleData/Sample Data - Xingyao MVP.xlsx";

    private static final String[] CITIES = {
            "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou",
            "Chengdu", "Wuhan", "Nanjing", "Suzhou", "Xi'an"
    };
    private static final String[] GROUPS = {
            "North Star Group", "East River Group", "South Bay Group", "West Link Group", "Central Auto Group"
    };
    private static final String[] MODELS = {"M7", "X5", "E3", "S9"};
    private static final String[] HORIZONS = {"0-30 days", "31-60 days", "61-90 days", "90+ days"};
    private static final String[] OPPORTUNITY_STAGES = {
            "Qualified", "Proposal", "Negotiation", "Won", "Lost"
    };
    private static final String[] LEAD_STAGES = {"New", "Qualified", "Converted", "Lost"};
    private static final String[] LEAD_SOURCES = {
            "Website", "WeChat", "Douyin", "Showroom", "Campaign", "Referral", "Xiaohongshu"
    };
    private static final String[] TASK_SUBJECTS = {
            "Initial Call", "Test Drive Invite", "Quote Follow-up", "Finance Plan", "Delivery Check"
    };
    private static final String[] TASK_STATUSES = {"Completed", "Pending", "In Progress", "Overdue"};
    private static final String[] CAMPAIGN_TYPES = {
            "Test Drive", "Online Live", "City Show", "Referral Drive", "Mall Booth"
    };
    private static final String[] EVENT_TYPES = {"Event", "Digital", "Retail", "Partner"};

    private SampleWorkbookGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path outputPath = Path.of(args.length > 0 ? args[0] : DEFAULT_OUTPUT).toAbsolutePath().normalize();
        new SampleWorkbookGenerator().write(outputPath);
        System.out.println("Generated sample workbook: " + outputPath);
    }

    void write(Path outputPath) throws IOException {
        Random random = new Random(RANDOM_SEED);
        List<DealerSeed> dealers = createDealers();
        List<OpportunitySeed> opportunities = new ArrayList<>();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            writeTargets(workbook.createSheet("AE Target Data"), dealers, random);
            writeOpportunities(workbook.createSheet("Opportunity"), dealers, random, opportunities);
            writeLeads(workbook.createSheet("Lead"), dealers, random);
            writeTasks(workbook.createSheet("Task"), opportunities, random);
            writeCampaigns(workbook.createSheet("Campaign"), dealers, random);

            Files.createDirectories(outputPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                workbook.write(outputStream);
            } finally {
                workbook.dispose();
            }
        }
    }

    private List<DealerSeed> createDealers() {
        List<DealerSeed> dealers = new ArrayList<>();
        for (int index = 1; index <= DEALER_COUNT; index++) {
            String city = CITIES[(index - 1) % CITIES.length];
            String group = GROUPS[(index - 1) % GROUPS.length];
            String code = "D%03d".formatted(index);
            String name = "Xingyao Dealer %03d(%s)".formatted(index, city);
            dealers.add(new DealerSeed(code, name, group));
        }
        return dealers;
    }

    private void writeTargets(Sheet sheet, List<DealerSeed> dealers, Random random) {
        writeHeader(sheet, "DealerCode", "DealerName", "DealerGroupName", "ProductModel", "TargetYear",
                "TargetMonth", "AsKTarget", "OpportunityWonCount", "OpportunityCreateCount");
        int rowIndex = 1;
        for (int monthOffset = 0; monthOffset < 12; monthOffset++) {
            LocalDate month = START_MONTH.plusMonths(monthOffset);
            for (DealerSeed dealer : dealers) {
                for (int modelIndex = 0; modelIndex < TARGET_MODEL_COUNT; modelIndex++) {
                    int target = 65 + random.nextInt(96);
                    int created = target + random.nextInt(45) - 12;
                    int won = Math.max(0, Math.min(created, (int) Math.round(target * (0.45 + random.nextDouble() * 0.7))));
                    Row row = sheet.createRow(rowIndex);
                    write(row, 0, dealer.code());
                    write(row, 1, dealer.name());
                    write(row, 2, dealer.group());
                    write(row, 3, MODELS[modelIndex]);
                    write(row, 4, month.getYear());
                    write(row, 5, month.getMonthValue());
                    write(row, 6, rowIndex % 53 == 0 ? null : target);
                    write(row, 7, won);
                    write(row, 8, Math.max(created, won));
                    rowIndex++;
                }
            }
        }
    }

    private void writeOpportunities(
            Sheet sheet,
            List<DealerSeed> dealers,
            Random random,
            List<OpportunitySeed> opportunities
    ) {
        writeHeader(sheet, "OpportunityId", "DealerCode", "DealerName", "ProductModel", "Purchase_Horizon__c",
                "StageName", "LeadSource", "CreatedDate", "ExpectedCloseDate", "Probability");
        for (int index = 1; index <= OPPORTUNITY_COUNT; index++) {
            DealerSeed dealer = dealers.get(random.nextInt(dealers.size()));
            LocalDate createdDate = randomDate(random);
            String id = "OPP-%06d".formatted(index);
            Row row = sheet.createRow(index);
            boolean invalidIdentity = index % 211 == 0;
            write(row, 0, invalidIdentity ? null : id);
            write(row, 1, dealer.code());
            write(row, 2, dealer.name());
            write(row, 3, index % 41 == 0 ? null : pick(MODELS, random));
            write(row, 4, index % 67 == 0 ? null : pick(HORIZONS, random));
            write(row, 5, pick(OPPORTUNITY_STAGES, random));
            write(row, 6, index % 47 == 0 ? null : pick(LEAD_SOURCES, random));
            write(row, 7, format(createdDate));
            write(row, 8, index % 37 == 0 ? null : format(createdDate.plusDays(10 + random.nextInt(80))));
            write(row, 9, index % 503 == 0 ? 125 : random.nextInt(101));
            if (!invalidIdentity) {
                opportunities.add(new OpportunitySeed(id, createdDate));
            }
        }
    }

    private void writeLeads(Sheet sheet, List<DealerSeed> dealers, Random random) {
        writeHeader(sheet, "LeadId", "DealerCode", "DealerName", "ProductModel", "LeadSource",
                "StageName", "CreatedDate", "IsConverted");
        for (int index = 1; index <= LEAD_COUNT; index++) {
            DealerSeed dealer = dealers.get(random.nextInt(dealers.size()));
            Row row = sheet.createRow(index);
            write(row, 0, index % 197 == 0 ? null : "LED-%06d".formatted(index));
            write(row, 1, index % 59 == 0 ? null : dealer.code());
            write(row, 2, index % 61 == 0 ? null : dealer.name());
            write(row, 3, index % 43 == 0 ? null : pick(MODELS, random));
            write(row, 4, index % 47 == 0 ? null : pick(LEAD_SOURCES, random));
            write(row, 5, pick(LEAD_STAGES, random));
            write(row, 6, index % 71 == 0 ? null : format(randomDate(random)));
            write(row, 7, random.nextInt(100) < 38 ? "true" : "false");
        }
    }

    private void writeTasks(Sheet sheet, List<OpportunitySeed> opportunities, Random random) {
        writeHeader(sheet, "TaskId", "OpportunityId", "Subject", "Status", "CreatedDate");
        for (int index = 1; index <= TASK_COUNT; index++) {
            OpportunitySeed opportunity = opportunities.get(random.nextInt(opportunities.size()));
            Row row = sheet.createRow(index);
            write(row, 0, index % 251 == 0 ? null : "TSK-%06d".formatted(index));
            write(row, 1, index % 389 == 0 ? "OPP-MISSING-%06d".formatted(index) : opportunity.id());
            write(row, 2, index % 97 == 0 ? null : pick(TASK_SUBJECTS, random));
            write(row, 3, pick(TASK_STATUSES, random));
            write(row, 4, format(opportunity.createdDate().plusDays(random.nextInt(21))));
        }
    }

    private void writeCampaigns(Sheet sheet, List<DealerSeed> dealers, Random random) {
        writeHeader(sheet, "CampaignId", "Name", "DealerCode", "DealerName", "ProductModel", "Type",
                "CampaignType", "CreatedDate", "TargetOpportunityAmount__c", "ActualOpportunityCount",
                "TargetOrderAmount__c", "WonOpportunityCount", "LeadCount", "TotalNewCustomerTarget");
        for (int index = 1; index <= CAMPAIGN_COUNT; index++) {
            DealerSeed dealer = dealers.get(random.nextInt(dealers.size()));
            int target = 20 + random.nextInt(70);
            int actual = Math.max(0, target + random.nextInt(35) - 10);
            Row row = sheet.createRow(index);
            write(row, 0, index % 211 == 0 ? null : "CAM-%05d".formatted(index));
            write(row, 1, index % 149 == 0 ? null : "Campaign %05d".formatted(index));
            write(row, 2, index % 53 == 0 ? null : dealer.code());
            write(row, 3, index % 57 == 0 ? null : dealer.name());
            write(row, 4, index % 43 == 0 ? null : pick(MODELS, random));
            write(row, 5, index % 61 == 0 ? null : pick(EVENT_TYPES, random));
            write(row, 6, pick(CAMPAIGN_TYPES, random));
            write(row, 7, format(randomDate(random).minusDays(random.nextInt(45))));
            write(row, 8, index % 67 == 0 ? null : target);
            write(row, 9, actual);
            write(row, 10, index % 73 == 0 ? null : Math.max(1, target / 3));
            write(row, 11, index % 79 == 0 ? null : Math.max(0, actual / 4));
            write(row, 12, index % 83 == 0 ? null : actual + random.nextInt(50));
            write(row, 13, index % 67 == 0 ? null : target);
        }
    }

    private LocalDate randomDate(Random random) {
        return START_MONTH.plusDays(random.nextInt(365));
    }

    private String pick(String[] values, Random random) {
        return values[random.nextInt(values.length)];
    }

    private String format(LocalDate date) {
        return date == null ? null : DATE_FORMAT.format(date);
    }

    private void writeHeader(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            write(row, index, headers[index]);
        }
    }

    private void write(Row row, int column, Object value) {
        Cell cell = row.createCell(column);
        if (value == null) {
            return;
        }
        switch (value) {
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            default -> cell.setCellValue(value.toString());
        }
    }

    private record DealerSeed(String code, String name, String group) {
    }

    private record OpportunitySeed(String id, LocalDate createdDate) {
    }
}
