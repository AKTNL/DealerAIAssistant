package com.brand.agentpoc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdentityInputPolicyTest {

    private final IdentityInputPolicy policy = new IdentityInputPolicy();

    @Test
    void normalizesUsernamesAndFallsBackDisplayNames() {
        assertThat(policy.normalizeUsername("  Dealer.Admin  ")).isEqualTo("dealer.admin");
        assertThat(policy.normalizeDisplayName(" ", "dealer.admin")).isEqualTo("dealer.admin");
    }

    @Test
    void acceptsUnicodeLettersWithoutAllowingUnsafePunctuation() {
        assertThat(policy.normalizeUsername("区域经理-01")).isEqualTo("区域经理-01");
        assertThatThrownBy(() -> policy.normalizeUsername("admin@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesUsernamePasswordAndDisplayNameBounds() {
        assertThatThrownBy(() -> policy.normalizeUsername("ab"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validatePassword("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validatePassword(" ".repeat(12)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.normalizeDisplayName("x".repeat(129), "fallback"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesOneMailboxAndRejectsHeaderOrDisplayNameSyntax() {
        assertThat(policy.normalizeEmail("  Analyst@Example.COM ")).isEqualTo("analyst@example.com");
        assertThat(policy.normalizeEmail("   ")).isNull();
        assertThatThrownBy(() -> policy.normalizeEmail("Analyst <analyst@example.com>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.normalizeEmail("one@example.com,two@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.normalizeEmail("analyst@example.com\r\nBcc:other@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
