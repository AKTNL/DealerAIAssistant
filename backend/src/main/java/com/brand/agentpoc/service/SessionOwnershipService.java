package com.brand.agentpoc.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionOwnershipService {

    private final Map<String, String> ownersBySession = new ConcurrentHashMap<>();

    public boolean claimOrVerify(String sessionId, String tokenSubject) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(tokenSubject)) {
            return false;
        }

        String normalizedSessionId = sessionId.trim();
        String normalizedSubject = tokenSubject.trim();
        String existing = ownersBySession.putIfAbsent(normalizedSessionId, normalizedSubject);
        return existing == null || existing.equals(normalizedSubject);
    }

    public boolean owns(String sessionId, String tokenSubject) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(tokenSubject)) {
            return false;
        }

        return tokenSubject.trim().equals(ownersBySession.get(sessionId.trim()));
    }

    public boolean release(String sessionId, String tokenSubject) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(tokenSubject)) {
            return false;
        }

        return ownersBySession.remove(sessionId.trim(), tokenSubject.trim());
    }
}
