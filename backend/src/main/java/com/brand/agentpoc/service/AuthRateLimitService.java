package com.brand.agentpoc.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthRateLimitService {

    private static final int MAX_FAILURES = 5;
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public AuthRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean isLimited(String clientKey) {
        AttemptState state = attempts.get(normalize(clientKey));
        if (state == null || state.cooldownUntil() == null) {
            return false;
        }

        if (Instant.now(clock).isBefore(state.cooldownUntil())) {
            return true;
        }

        attempts.remove(normalize(clientKey), state);
        return false;
    }

    public long retryAfterSeconds(String clientKey) {
        AttemptState state = attempts.get(normalize(clientKey));
        if (state == null || state.cooldownUntil() == null) {
            return 0;
        }

        long seconds = Duration.between(Instant.now(clock), state.cooldownUntil()).toSeconds();
        return Math.max(seconds, 0);
    }

    public void recordFailure(String clientKey) {
        String normalized = normalize(clientKey);
        attempts.compute(normalized, (key, state) -> {
            Instant now = Instant.now(clock);
            if (state != null && state.cooldownUntil() != null && now.isBefore(state.cooldownUntil())) {
                return state;
            }

            int failures = state == null ? 1 : state.failureCount() + 1;
            Instant cooldownUntil = failures >= MAX_FAILURES ? now.plus(COOLDOWN) : null;
            return new AttemptState(failures, cooldownUntil);
        });
    }

    public void recordSuccess(String clientKey) {
        attempts.remove(normalize(clientKey));
    }

    private String normalize(String clientKey) {
        return StringUtils.hasText(clientKey) ? clientKey.trim() : "unknown";
    }

    private record AttemptState(int failureCount, Instant cooldownUntil) {
    }
}
