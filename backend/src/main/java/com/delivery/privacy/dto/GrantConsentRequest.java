package com.delivery.privacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/me/consents/{purpose}: the text version the subject accepted; must be the current version. */
public record GrantConsentRequest(@NotBlank @Size(max = 40) String version) {
}
