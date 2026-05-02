package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppProperties appProperties;

    public AuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public boolean verifyAccessKey(String inputKey) {
        return appProperties.getAuth().getAccessKey().equals(inputKey);
    }
}

