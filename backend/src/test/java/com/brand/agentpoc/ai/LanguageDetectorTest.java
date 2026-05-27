package com.brand.agentpoc.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageDetectorTest {

    private final LanguageDetector languageDetector = new LanguageDetector();

    @Test
    void detectsChineseTextAsZh() {
        assertThat(languageDetector.detectLanguage("本月哪些经销商目标达成率最低？")).isEqualTo("zh");
    }

    @Test
    void detectsEnglishTextAsEn() {
        assertThat(languageDetector.detectLanguage("Which dealers have the lowest target achievement?")).isEqualTo("en");
    }

    @Test
    void defaultsBlankTextToZh() {
        assertThat(languageDetector.detectLanguage("   ")).isEqualTo("zh");
    }
}
