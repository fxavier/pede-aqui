package com.delivery.rating.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The vendor's single public reply to a rating (at most 500 characters). */
public record VendorReplyRequest(@NotBlank @Size(max = 500) String reply) {}
