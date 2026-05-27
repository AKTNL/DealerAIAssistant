package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyticsScenarioCatalogTest {

    @Test
    void opportunityFunnelExamplesMatchCorrectedDocumentQuestions() {
        AnalyticsScenarioCatalog.ScenarioWorkflow workflow =
                AnalyticsScenarioCatalog.forScenario(AnalyticsPlan.Scenario.OPPORTUNITY_FUNNEL);

        assertThat(workflow.examples("zh")).containsExactly(
                "Vega GT 和 Terra XL 在不同购车周期下的商机阶段分布如何？",
                "最近已战败商机主要集中在哪些车型，核心战败原因是什么？"
        );
        assertThat(workflow.examples("en")).containsExactly(
                "How are Vega GT and Terra XL opportunities distributed by purchase horizon and stage?",
                "Which models dominate recent Closed Lost opportunities, and what are the key lost reasons?"
        );
    }
}
