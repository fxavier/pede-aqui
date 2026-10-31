package com.delivery.privacy.controller;

import com.delivery.privacy.dto.ProfileSettingsResponse;
import com.delivery.privacy.dto.UpdateMyProfileRequest;
import com.delivery.privacy.service.ProfileSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile settings (LAC-19, W7-C): any authenticated user with a profile in the tenant. GET /me itself
 * stays in the auth module; this controller adds PATCH /me and GET /me/profile (settings incl. notification opt-ins).
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class MyProfileController {
    private final ProfileSettingsService service;

    public MyProfileController(ProfileSettingsService service) {
        this.service = service;
    }

    @Operation(operationId = "getMyProfileSettings", summary = "Get the caller's profile settings (display name, locale, notification preferences)")
    @GetMapping("/profile")
    public ProfileSettingsResponse profile() {
        return service.current();
    }

    @Operation(operationId = "updateMyProfile", summary = "Update the caller's display name, preferred locale and/or notification preferences",
            description = "Partial update: omitted/null fields are unchanged. Display name and locale are mirrored to Keycloak. "
                    + "E-mail and phone are not editable here: e-mail changes go through the Keycloak account console "
                    + "(with verification) and the phone through the phone + SMS login.")
    @PatchMapping
    public ProfileSettingsResponse update(@Valid @RequestBody UpdateMyProfileRequest request) {
        return service.update(request);
    }
}
