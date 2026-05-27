package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionMemoryServiceTest {

    @Test
    void keepsOnlyTheLatestTwentyMessagesInTheSlidingWindow() {
        SessionMemoryService sessionMemoryService = new SessionMemoryService(new InMemoryChatMemory());

        for (int i = 1; i <= 11; i++) {
            sessionMemoryService.addUserMessage("s1", "user-" + i);
            sessionMemoryService.addAssistantMessage("s1", "assistant-" + i);
        }

        List<String> messages = sessionMemoryService.getMessages("s1");

        assertThat(messages).hasSize(20);
        assertThat(messages.getFirst()).isEqualTo("USER:user-2");
        assertThat(messages.getLast()).isEqualTo("ASSISTANT:assistant-11");
        assertThat(messages).doesNotContain("USER:user-1", "ASSISTANT:assistant-1");
    }

    @Test
    void clearsOnlyTheRequestedSessionMemory() {
        SessionMemoryService sessionMemoryService = new SessionMemoryService(new InMemoryChatMemory());

        sessionMemoryService.addUserMessage("s1", "user-1");
        sessionMemoryService.addAssistantMessage("s1", "assistant-1");
        sessionMemoryService.addUserMessage("s2", "user-2");

        sessionMemoryService.clearSession("s1");

        assertThat(sessionMemoryService.getMessages("s1")).isEmpty();
        assertThat(sessionMemoryService.getMessages("s2")).containsExactly("USER:user-2");
    }
}
