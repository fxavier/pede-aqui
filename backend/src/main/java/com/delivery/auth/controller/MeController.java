package com.delivery.auth.controller;

import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.service.AppUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the current authenticated user's profile. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final AppUserProfileService service;

    public MeController(AppUserProfileService service) {
        this.service = service;
    }

    @Operation(summary = "Get current user profile and roles")
    @GetMapping
    public MeResponse me() {
        return service.getCurrentUserProfile();
    }
}
