package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /cases/{id}/override}'s body — see
 * {@code module-06-agreement-management-docs/uc-08-override-case.md}'s Contract section.
 * {@code newStatus} is a free string here (not the enum) so an unrecognised value is this
 * module's own {@code 400} ("newStatus must be PENDING or DECLINED") rather than Jackson's
 * generic enum-deserialization error.
 */
public record OverrideRequest(
        @NotBlank String newStatus,
        @NotBlank String reason,
        @NotBlank String operator) {
}
