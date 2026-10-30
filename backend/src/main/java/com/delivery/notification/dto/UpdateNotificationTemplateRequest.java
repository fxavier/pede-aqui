package com.delivery.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tenant override of a notification template; {@code title} is ignored for SMS. Placeholders: {@code {orderReference}}. */
public record UpdateNotificationTemplateRequest(@Size(max = 180) String title, @NotBlank @Size(max = 2000) String body,
                                                @NotNull Boolean active) {}
