package com.brand.agentpoc.config;

import com.brand.agentpoc.ai.PromptFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public PromptFactory promptFactory() {
        return new PromptFactory();
    }
}

