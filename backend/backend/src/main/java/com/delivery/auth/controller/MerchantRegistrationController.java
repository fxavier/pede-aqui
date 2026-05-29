package com.delivery.auth.controller;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.service.MerchantRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes merchant self-registration functionality. */
@RestController
@RequestMapping("/api/v1/register")
public class MerchantRegistrationController {
    private final MerchantRegistrationService service;

    public MerchantRegistrationController(MerchantRegistrationService service) {
        this.service = service;
    }

    @Operation(summary = "Register a new merchant account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantRegistrationResponse register(@Valid @RequestBody MerchantRegistrationRequest request) {
        return service.register(request);
    }
}