package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What an operator POSTs to {@code /cases/{id}/resend} — see
 * {@code docs/uc-04-pending-expired-queue.md}'s "Contract" section.
 *
 * @param operator who asked for the resend — recorded as the history row's {@code actor}
 */
public record ResendRequest(@NotBlank String operator) {
}
