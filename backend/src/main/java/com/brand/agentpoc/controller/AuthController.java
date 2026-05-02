package com.brand.agentpoc.controller;

import com.brand.agentpoc.dto.request.AuthVerifyRequest;
import com.brand.agentpoc.dto.response.AuthVerifyResponse;
import com.brand.agentpoc.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify")
    public AuthVerifyResponse verify(@Valid @RequestBody AuthVerifyRequest request) {
        return new AuthVerifyResponse(authService.verifyAccessKey(request.key()));
    }
}

