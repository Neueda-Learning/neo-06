package com.neobank.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What {@code POST /cases/{id}/override} receives — see
 * {@code module-06-agreement-management-docs/uc-08-override-case.md}'s "Contract" section.
 *
 * <p>The operator's justification is mandatory (AC 2) — the endpoint returns {@code 400}
 * without either {@code reason} or {@code operator}. {@code newStatus} must be either
 * {@code PENDING} or {@code DECLINED} — {@code SIGNED} is never a legal override target.</p>
 */
public record OverrideCommand(
        @JsonProperty("newStatus") String newStatus,
        @JsonProperty("reason") String reason,
        @JsonProperty("operator") String operator) {
}