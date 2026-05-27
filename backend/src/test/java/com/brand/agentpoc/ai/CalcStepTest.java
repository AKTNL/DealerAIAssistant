package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CalcStepTest {

    @Test
    void nullInputsAreNormalizedToEmptyStrings() {
        CalcStep step = new CalcStep(null, null, null, null);

        assertThat(step.labelZh()).isEmpty();
        assertThat(step.labelEn()).isEmpty();
        assertThat(step.detailZh()).isEmpty();
        assertThat(step.detailEn()).isEmpty();
    }

    @Test
    void bilingualFieldsArePreserved() {
        CalcStep step = new CalcStep("加载数据", "Load data", "共 100 条", "100 records");

        assertThat(step.labelZh()).isEqualTo("加载数据");
        assertThat(step.labelEn()).isEqualTo("Load data");
        assertThat(step.detailZh()).isEqualTo("共 100 条");
        assertThat(step.detailEn()).isEqualTo("100 records");
    }

    @Test
    void recordsWithSameValuesAreEqual() {
        CalcStep a = new CalcStep("加载", "Load", "100 条", "100 records");
        CalcStep b = new CalcStep("加载", "Load", "100 条", "100 records");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
