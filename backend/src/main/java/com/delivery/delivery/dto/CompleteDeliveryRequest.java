package com.delivery.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request used by couriers to complete delivery with the customer code. */
public record CompleteDeliveryRequest(@NotNull @Pattern(regexp = "[0-9]{6}") String confirmationCode) {}
