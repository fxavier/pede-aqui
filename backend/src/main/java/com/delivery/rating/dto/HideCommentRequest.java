package com.delivery.rating.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Moderation reason for hiding a rating comment (kept on the rating, never in the audit text). */
public record HideCommentRequest(@NotBlank @Size(max = 200) String reason) {}
