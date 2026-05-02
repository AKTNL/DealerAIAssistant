package com.brand.agentpoc.ai;

import org.springframework.stereotype.Component;

@Component
public class LanguageDetector {

    public String detectLanguage(String message) {
        if (message == null || message.isBlank()) {
            return "zh";
        }

        long chineseCount = message.codePoints()
                .filter(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                .count();

        return chineseCount > 0 ? "zh" : "en";
    }
}

