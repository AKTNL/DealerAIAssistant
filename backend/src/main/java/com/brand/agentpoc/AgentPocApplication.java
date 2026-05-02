package com.brand.agentpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPocApplication.class, args);
    }
}

