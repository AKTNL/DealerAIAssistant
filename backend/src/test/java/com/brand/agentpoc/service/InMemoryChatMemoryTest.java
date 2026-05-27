package com.brand.agentpoc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryChatMemoryTest {

    @Test
    void keepsATwentyMessageSlidingWindowPerSession() {
        InMemoryChatMemory chatMemory = new InMemoryChatMemory();

        for (int i = 1; i <= 11; i++) {
            chatMemory.addUserMessage("s1", "user-" + i);
            chatMemory.addAssistantMessage("s1", "assistant-" + i);
        }

        List<String> messages = chatMemory.getMessages("s1");

        assertThat(messages).hasSize(20);
        assertThat(messages.getFirst()).isEqualTo("USER:user-2");
        assertThat(messages.getLast()).isEqualTo("ASSISTANT:assistant-11");
    }

    @Test
    void clearsOnlyTheRequestedSession() {
        InMemoryChatMemory chatMemory = new InMemoryChatMemory();

        chatMemory.addUserMessage("s1", "user-1");
        chatMemory.addAssistantMessage("s2", "assistant-2");

        chatMemory.clearSession("s1");

        assertThat(chatMemory.getMessages("s1")).isEmpty();
        assertThat(chatMemory.getMessages("s2")).containsExactly("ASSISTANT:assistant-2");
    }
}
