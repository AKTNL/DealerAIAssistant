package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class ExcelImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private final AppProperties appProperties;

    public ExcelImportService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Excel import scaffold initialized. Configured path: {}", appProperties.getExcel().getPath());
    }
}

