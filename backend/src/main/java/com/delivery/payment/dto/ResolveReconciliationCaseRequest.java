package com.delivery.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Closes a reconciliation case with a short operational note (no personal data). */
public record ResolveReconciliationCaseRequest(@NotBlank @Size(max = 500) String note) {
}
