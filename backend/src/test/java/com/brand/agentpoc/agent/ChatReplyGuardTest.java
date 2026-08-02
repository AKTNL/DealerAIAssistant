package com.brand.agentpoc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.brand.agentpoc.ai.LanguageDetector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatReplyGuardTest {

    private ChatReplyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ChatReplyGuard(mock(LanguageDetector.class));
    }

    @Test
    void addsTwoFollowUpQuestionsToARegularReply() {
        String normalized = guard.ensureFollowUpQuestions("CRM hygiene needs consistency.", "en", false);

        assertThat(normalized)
                .contains("FOLLOW_UP_QUESTIONS:")
                .contains("1.")
                .contains("2.");
        assertThat(guard.extractFollowUpsFromReply(normalized)).hasSize(2);
    }

    @Test
    void extractsAnalyticsTopicKeywordsAndMatchesRelevantFollowUp() {
        List<String> keywords = guard.extractTopicKeywords(
                "The opportunity funnel shows a conversion drop-off.",
                "en"
        );

        assertThat(keywords).contains("opportunity funnel", "conversion", "drop-off");
        assertThat(guard.isStronglyRelevant("Which opportunity funnel stage needs attention?", keywords))
                .isTrue();
        assertThat(guard.isStronglyRelevant("What is the weather today?", keywords)).isFalse();
    }

    @Test
    void rejectsBlankModelReplies() {
        assertThatThrownBy(() -> guard.ensureFollowUpQuestions("  ", "en", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reply is blank after model generation.");
    }
}
