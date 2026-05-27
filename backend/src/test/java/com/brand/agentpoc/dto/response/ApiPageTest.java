package com.brand.agentpoc.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ApiPageTest {

    @Test
    void ofCreatesPageWithGivenItems() {
        List<String> items = List.of("a", "b", "c");
        ApiPage<String> page = ApiPage.of(items, 100, 1, 20);

        assertThat(page.items()).containsExactly("a", "b", "c");
        assertThat(page.total()).isEqualTo(100);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
    }

    @Test
    void emptyCreatesPageWithNoItems() {
        ApiPage<String> page = ApiPage.empty(1, 10, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(0);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(10);
    }
}
