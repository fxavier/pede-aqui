package com.delivery.dispatch.dto;

import java.time.Instant;

/**
 * The courier's GPS consent state (LAC-40): the version the app must show/accept, what was accepted and when, and
 * whether it is valid (accepted version = current version).
 */
public record LocationConsentResponse(String currentVersion, String acceptedVersion, Instant acceptedAt, boolean valid) {}
