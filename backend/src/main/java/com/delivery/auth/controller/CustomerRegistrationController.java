package com.delivery.auth.controller;

import com.delivery.auth.dto.CustomerRegistrationRequest;
import com.delivery.auth.dto.CustomerRegistrationResponse;
import com.delivery.auth.service.CustomerRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes customer self-registration (no auth required). */
@RestController
@RequestMapping("/api/v1/customers/register")
public class CustomerRegistrationController {

    private final CustomerRegistrationService service;

    public CustomerRegistrationController(CustomerRegistrationService service) {
        this.service = service;
    }

    @Operation(summary = "Register a new customer account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerRegistrationResponse register(@Valid @RequestBody CustomerRegistrationRequest request) {
        return service.register(request);
    }
}
