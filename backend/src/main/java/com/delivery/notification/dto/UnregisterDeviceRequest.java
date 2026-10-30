package com.delivery.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Unregisters the caller's push token (the apps call it on logout). Sent in the body so the token never hits access logs. */
public record UnregisterDeviceRequest(@NotBlank @Size(max = 4096) String token) {}
