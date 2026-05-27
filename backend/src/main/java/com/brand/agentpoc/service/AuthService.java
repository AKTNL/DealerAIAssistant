package com.brand.agentpoc.service;

import com.brand.agentpoc.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AppProperties appProperties;

    public AuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public boolean verifyAccessKey(String inputKey) {
        String configured = appProperties.getAuth().getAccessKey();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(inputKey)) {
            return false;
        }

        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                inputKey.getBytes(StandardCharsets.UTF_8));
    }
}

