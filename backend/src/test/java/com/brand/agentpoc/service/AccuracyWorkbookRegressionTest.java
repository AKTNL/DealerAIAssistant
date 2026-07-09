package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.AgentPocApplication;
import com.brand.agentpoc.dto.request.ChatRequest;
import com.brand.agentpoc.repository.CampaignRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.repository.LeadRepository;
import com.brand.agentpoc.repository.OpportunityRepository;
import com.brand.agentpoc.repository.TargetRepository;
import com.brand.agentpoc.repository.TaskRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccuracyWorkbookRegressionTest {

    private static final String QUESTION_SHEET = "\u6d4b\u8bd5\u9898\u5e93";
    private static final String BOUNDARY_CATEGORY = "\u8fb9\u754c\u95ee\u9898";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?%?");
    private static final Pattern DEALER_PATTERN = Pattern.compile("\u7ecf\u9500\u5546[A-Z]+(?:[\\(\uff08][^\\)\uff09]+[\\)\uff09])?");
    private static final List<String> IMPORTANT_PHRASES = List.of(
            "Aurora S",
            "Vega Cabrio",
            "Vega GT",
            "Terra XL",
            "Terra X",
            "Walk-In",
            "Dealer Online",
            "Dealer Offline",
            "XY Online",
            "CustomerReferral",
            "Qualification & Discovery",
            "Closed Lost",
            "Closed Won",
            "Proposal & Negotiation",
            "Sales Confirmation",
            "More than 10 Months",
            "6 to 10 Months",
            "Completed",
            "Planned",
            "Cancelled",
            "Not Started",
            "Qualified",
            "Event",
            "Call",
            "Web",
            "EV",
            "BR"
    );

    private ConfigurableApplicationContext context;
    private ChatService chatService;
    private DealerRepository dealerRepository;
    private OpportunityRepository opportunityRepository;
    private LeadRepository leadRepository;
    private TaskRepository taskRepository;
    private CampaignRepository campaignRepository;
    private TargetRepository targetRepository;

    @BeforeAll
    void startApplication() {
        SpringApplication application = new SpringApplication(AgentPocApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseName = "accuracy_workbook_" + UUID.randomUUID().toString().replace("-", "");

        context = application.run(
                "--spring.datasource.url=jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--app.excel.path=classpath:Sample Data.xlsx"
        );

        chatService = context.getBean(ChatService.class);
        dealerRepository = context.getBean(DealerRepository.class);
        opportunityRepository = context.getBean(OpportunityRepository.class);
        leadRepository = context.getBean(LeadRepository.class);
        taskRepository = context.getBean(TaskRepository.class);
        campaignRepository = context.getBean(CampaignRepository.class);
        targetRepository = context.getBean(TargetRepository.class);
    }

    @AfterAll
    void closeApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void importedSampleDataKeepsTheAccuracyWorkbookBaselineCounts() {
        assertThat(dealerRepository.count()).isEqualTo(112);
        assertThat(targetRepository.count()).isEqualTo(5_088);
        assertThat(opportunityRepository.count()).isEqualTo(6_198);
        assertThat(leadRepository.count()).isEqualTo(1_898);
        assertThat(taskRepository.count()).isEqualTo(57_582);
        assertThat(campaignRepository.count()).isEqualTo(715);
    }

    @Test
    void workbookQuestionsHitTheExpectedDeterministicAnswerPoints() throws Exception {
        List<WorkbookQuestion> questions = readWorkbookQuestions();
        List<String> failures = new ArrayList<>();

        for (WorkbookQuestion question : questions) {
            String reply = chat(question);
            if (BOUNDARY_CATEGORY.equals(question.category())) {
                collectBoundaryFailures(question, reply, failures);
                continue;
            }

            List<String> missingHits = missingExpectedHits(question, reply);
            if (!missingHits.isEmpty()) {
                failures.add("row " + question.rowNumber()
                        + " missing " + missingHits
                        + " for question " + question.question()
                        + " reply=" + abbreviate(reply));
            }
        }

        assertThat(failures).isEmpty();
    }

    @Test
    void paraphrasedWorkbookIntentsStillUseSemanticAnalyticsRoutes() {
        List<ParaphraseCase> paraphrases = List.of(
                new ParaphraseCase(
                        "\u5168\u91cf\u6837\u672c\u4e2d\u54ea\u6b3e\u8f66\u578b\u5356\u5f97\u6700\u597d\uff1f",
                        List.of("Aurora S", "4175")
                ),
                new ParaphraseCase(
                        "\u6837\u672c\u91cc\u5546\u673a\u3001\u7ebf\u7d22\u3001\u8ddf\u8fdb\u4efb\u52a1\u3001\u5e02\u573a\u6d3b\u52a8\u5206\u522b\u6709\u591a\u5c11\uff1f",
                        List.of("6198", "1898", "57582", "715")
                ),
                new ParaphraseCase(
                        "\u54ea\u79cd\u6e20\u9053\u5e26\u6765\u7684\u7ebf\u7d22\u8f6c\u5316\u7387\u6700\u9ad8\uff1f",
                        List.of("Walk-In", "78.3%", "Web", "75.4%")
                )
        );
        List<String> failures = new ArrayList<>();

        for (int index = 0; index < paraphrases.size(); index++) {
            ParaphraseCase paraphrase = paraphrases.get(index);
            String reply = chatService.chat(new ChatRequest("accuracy-paraphrase-" + index, paraphrase.question(), "", "", ""));
            String normalizedReply = normalizeForContains(reply);
            List<String> missingHits = paraphrase.expectedHits().stream()
                    .filter(hit -> !containsNormalized(normalizedReply, hit))
                    .toList();
            if (!missingHits.isEmpty()) {
                failures.add("paraphrase " + (index + 1)
                        + " missing " + missingHits
                        + " reply=" + abbreviate(reply));
            }
        }

        assertThat(failures).isEmpty();
    }

    private String chat(WorkbookQuestion question) {
        return chatService.chat(new ChatRequest(
                "accuracy-workbook-row-" + question.rowNumber(),
                question.question(),
                "",
                "",
                ""
        ));
    }

    private List<WorkbookQuestion> readWorkbookQuestions() throws Exception {
        Path workbookPath = findAccuracyWorkbook();
        try (InputStream inputStream = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            assertThat(sheet.getSheetName()).isEqualTo(QUESTION_SHEET);
            assertThat(sheet.getLastRowNum()).isEqualTo(51);
            assertHeader(sheet.getRow(0), formatter);

            List<WorkbookQuestion> questions = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                questions.add(new WorkbookQuestion(
                        rowIndex + 1,
                        cellText(row, 0, formatter),
                        cellText(row, 1, formatter),
                        cellText(row, 2, formatter),
                        cellText(row, 3, formatter)
                ));
            }

            assertThat(questions).hasSize(51);
            return questions;
        }
    }

    private void assertHeader(Row header, DataFormatter formatter) {
        assertThat(cellText(header, 0, formatter)).isEqualTo("\u7c7b\u522b");
        assertThat(cellText(header, 1, formatter)).isEqualTo("\u95ee\u9898");
        assertThat(cellText(header, 2, formatter)).isEqualTo("\u6807\u51c6\u7b54\u6848");
        assertThat(cellText(header, 3, formatter)).isEqualTo("\u5173\u952e\u547d\u4e2d\u70b9/\u8bc4\u5206\u53c2\u8003");
    }

    private Path findAccuracyWorkbook() throws IOException {
        Path mockservice = Path.of("..", "mockservice").toAbsolutePath().normalize();
        if (!Files.isDirectory(mockservice)) {
            mockservice = Path.of("mockservice").toAbsolutePath().normalize();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mockservice, "DealerAIAssistant_*.xlsx")) {
            for (Path path : stream) {
                return path;
            }
        }
        throw new IOException("Accuracy workbook not found in " + mockservice);
    }

    private String cellText(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(columnIndex)).trim();
    }

    private List<String> missingExpectedHits(WorkbookQuestion question, String reply) {
        String normalizedReply = normalizeForContains(reply);
        List<String> expectedHits = expectedHits(question);
        return expectedHits.stream()
                .filter(hit -> !containsNormalized(normalizedReply, hit))
                .toList();
    }

    private List<String> expectedHits(WorkbookQuestion question) {
        Set<String> hits = new LinkedHashSet<>();
        String hitSource = question.keyHit();
        if (hitSource.contains("\u9636\u6bb5\u6570\u91cf")) {
            hitSource = hitSource + " " + question.standardAnswer();
        }

        addNumbers(hits, hitSource);
        addDealerNames(hits, question.keyHit());
        addDealerNames(hits, question.standardAnswer());
        addImportantPhrases(hits, question.keyHit());
        addImportantPhrases(hits, question.standardAnswer());

        return List.copyOf(hits);
    }

    private void addNumbers(Set<String> hits, String source) {
        Matcher matcher = NUMBER_PATTERN.matcher(source == null ? "" : source);
        while (matcher.find()) {
            hits.add(matcher.group());
        }
    }

    private void addDealerNames(Set<String> hits, String source) {
        Matcher matcher = DEALER_PATTERN.matcher(source == null ? "" : source);
        while (matcher.find()) {
            String dealer = matcher.group()
                    .replaceFirst("[\\(\uff08].*$", "");
            hits.add(dealer);
        }
    }

    private void addImportantPhrases(Set<String> hits, String source) {
        String haystack = source == null ? "" : source;
        for (String phrase : IMPORTANT_PHRASES) {
            if (haystack.contains(phrase)) {
                hits.add(phrase);
            }
        }
    }

    private void collectBoundaryFailures(WorkbookQuestion question, String reply, List<String> failures) {
        String normalizedReply = normalizeForContains(reply);
        String normalizedQuestion = normalizeForContains(question.question());

        if (normalizedQuestion.contains("hello")) {
            List<String> expected = List.of("dealer AI analytics assistant", "target achievement");
            List<String> missing = expected.stream()
                    .filter(hit -> !containsNormalized(normalizedReply, hit))
                    .toList();
            if (!missing.isEmpty() || normalizedReply.contains("6198")) {
                failures.add("row " + question.rowNumber()
                        + " should introduce the assistant without sample metrics, missing=" + missing
                        + " reply=" + abbreviate(reply));
            }
            return;
        }

        if (normalizedQuestion.contains(normalizeForContains("\u5ba2\u6237A"))
                || normalizedQuestion.contains("xyz")) {
            if (!containsAnyNormalized(normalizedReply, "\u672a\u627e\u5230", "could not find", "couldn't find")
                    || containsAnyNormalized(normalizedReply, "\u6838\u5fc3\u7ed3\u8bba", "data support")) {
                failures.add("row " + question.rowNumber()
                        + " should return entity-not-found without analytics data reply=" + abbreviate(reply));
            }
            return;
        }

        if (!containsAnyNormalized(normalizedReply, "\u4e1a\u52a1\u8303\u56f4", "business scope")
                || containsAnyNormalized(normalizedReply, "\u5feb\u901f\u6392\u5e8f", "quick sort")) {
            failures.add("row " + question.rowNumber()
                    + " should refuse out-of-scope content reply=" + abbreviate(reply));
        }
    }

    private boolean containsAnyNormalized(String normalizedReply, String... hits) {
        for (String hit : hits) {
            if (containsNormalized(normalizedReply, hit)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsNormalized(String normalizedReply, String hit) {
        String normalizedHit = normalizeForContains(hit);
        if (normalizedReply.contains(normalizedHit)) {
            return true;
        }
        if (normalizedHit.endsWith(".0%")) {
            return normalizedReply.contains(normalizedHit.replace(".0%", "%"));
        }
        if (normalizedHit.endsWith("%") && normalizedHit.indexOf('.') < 0) {
            return normalizedReply.contains(normalizedHit.replace("%", ".0%"));
        }
        return false;
    }

    private String normalizeForContains(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replace("&amp;", "&")
                .replace(",", "")
                .replace("\uff0c", "")
                .replaceAll("\\s+", "");
    }

    private String abbreviate(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 600) {
            return normalized;
        }
        return normalized.substring(0, 600) + "...";
    }

    private record WorkbookQuestion(
            int rowNumber,
            String category,
            String question,
            String standardAnswer,
            String keyHit
    ) {
    }

    private record ParaphraseCase(String question, List<String> expectedHits) {
    }
}
