package com.brand.agentpoc.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OperationalAssetsTest {

    private static final Path OPERATIONS_ROOT = Path.of("..", "ops");

    @Test
    void alertRulesHaveDebounceAndActionableAnnotations() throws IOException {
        Map<?, ?> root = yaml(OPERATIONS_ROOT.resolve("prometheus/agentpoc-alerts.yml"));
        List<?> groups = list(root.get("groups"));
        Set<String> alertNames = new HashSet<>();

        for (Object groupValue : groups) {
            Map<?, ?> group = map(groupValue);
            for (Object ruleValue : list(group.get("rules"))) {
                Map<?, ?> rule = map(ruleValue);
                String alertName = text(rule.get("alert"));
                assertThat(alertName).isNotBlank();
                assertThat(alertNames.add(alertName)).as("unique alert name %s", alertName).isTrue();
                assertThat(text(rule.get("for"))).as("debounce for %s", alertName).isNotBlank();
                Map<?, ?> annotations = map(rule.get("annotations"));
                assertThat(annotations.keySet().stream().map(Object::toString)).as(
                                "actionable annotations for %s", alertName)
                        .contains("impact", "threshold", "diagnostic_url", "runbook_url");
                assertThat(text(annotations.get("diagnostic_url"))).startsWith("https://");
                assertThat(text(annotations.get("runbook_url"))).startsWith("https://");
            }
        }

        assertThat(alertNames)
                .contains(
                        "AgentPocReadinessFailed",
                        "AgentPocSlowQueriesDetected",
                        "AgentPocModelProviderDegraded",
                        "AgentPocReportJobBacklog",
                        "AgentPocReportDeliveryFailures"
                )
                .hasSizeGreaterThanOrEqualTo(10);

        Map<?, ?> slowQueryAlert = alert(groups, "AgentPocSlowQueriesDetected");
        assertThat(text(slowQueryAlert.get("expr")))
                .contains("sum(increase(agentpoc_database_slow_query_total[10m]))")
                .contains(">= 5");
    }

    @Test
    void alertmanagerTemplateGroupsRepeatsInhibitsAndSendsRecovery() throws IOException {
        Map<?, ?> root = yaml(OPERATIONS_ROOT.resolve("alertmanager/alertmanager.example.yml"));
        Map<?, ?> route = map(root.get("route"));

        assertThat(text(route.get("group_wait"))).isEqualTo("30s");
        assertThat(text(route.get("group_interval"))).isEqualTo("5m");
        assertThat(text(route.get("repeat_interval"))).isEqualTo("4h");
        assertThat(list(root.get("inhibit_rules"))).isNotEmpty();

        Map<?, ?> receiver = map(list(root.get("receivers")).getFirst());
        Map<?, ?> webhook = map(list(receiver.get("webhook_configs")).getFirst());
        assertThat(webhook.get("send_resolved")).isEqualTo(Boolean.TRUE);
        assertThat(text(webhook.get("url_file"))).isEqualTo("/etc/alertmanager/secrets/operations-webhook-url");
    }

    private Map<?, ?> yaml(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return map(new Yaml().load(input));
        }
    }

    private Map<?, ?> alert(List<?> groups, String alertName) {
        return groups.stream()
                .map(this::map)
                .flatMap(group -> list(group.get("rules")).stream())
                .map(this::map)
                .filter(rule -> alertName.equals(text(rule.get("alert"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing alert " + alertName));
    }

    private Map<?, ?> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private List<?> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<?>) value;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
