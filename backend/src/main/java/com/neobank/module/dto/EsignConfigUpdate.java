package com.neobank.module.dto;

import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;

/**
 * {@code PUT /esign/config}'s request body — a PARTIAL update, matching the brief's own example
 * ({@code {"mode":"SILENT","demoExpirySeconds":30}}, {@code delaySeconds}/{@code autoOutcome}
 * omitted). Any field left {@code null} keeps its current value — see
 * {@link com.neobank.module.model.EsignProviderConfig#applyUpdate}.
 */
public record EsignConfigUpdate(
        EsignMode mode,
        Integer delaySeconds,
        EsignOutcome autoOutcome,
        Integer demoExpirySeconds) {
}
