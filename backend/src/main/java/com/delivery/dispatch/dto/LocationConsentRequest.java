package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The courier accepts the GPS consent text of the given version (must be the current one, LAC-40). */
public record LocationConsentRequest(@NotBlank @Size(max = 40) String version) {}
