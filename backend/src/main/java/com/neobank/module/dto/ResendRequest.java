package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /cases/{id}/resend}'s body — see UC 04's Contract section. */
public record ResendRequest(@NotBlank String operator) {
}
