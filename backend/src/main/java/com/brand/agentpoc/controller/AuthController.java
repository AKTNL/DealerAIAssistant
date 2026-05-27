package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.AuthVerifyRequest;
import com.brand.agentpoc.dto.response.AuthVerifyResponse;
import com.brand.agentpoc.service.AuthRateLimitService;
import com.brand.agentpoc.service.AuthService;
import com.brand.agentpoc.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;
    private final SessionTokenService sessionTokenService;

    public AuthController(
            AuthService authService,
            AuthRateLimitService authRateLimitService,
            SessionTokenService sessionTokenService
    ) {
        this.authService = authService;
        this.authRateLimitService = authRateLimitService;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthVerifyResponse> verify(
            @Valid @RequestBody AuthVerifyRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientKey = servletRequest.getRemoteAddr();
        if (authRateLimitService.isLimited(clientKey)) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(authRateLimitService.retryAfterSeconds(clientKey)))
                    .body(AuthVerifyResponse.failure());
        }

        if (!authService.verifyAccessKey(request.key())) {
            authRateLimitService.recordFailure(clientKey);
            return ResponseEntity.ok(AuthVerifyResponse.failure());
        }

        try {
            SessionTokenService.IssuedToken issuedToken = sessionTokenService.issueToken();
            authRateLimitService.recordSuccess(clientKey);
            return ResponseEntity.ok(AuthVerifyResponse.success(issuedToken.token(), issuedToken.expiresAt()));
        } catch (IllegalStateException exception) {
            log.error("Unable to issue session token after successful access-key verification: {}",
                    exception.getMessage());
            return ResponseEntity.ok(AuthVerifyResponse.failure());
        }
    }
}
