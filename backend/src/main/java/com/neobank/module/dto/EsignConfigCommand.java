package com.neobank.module.dto;

import com.neobank.module.integrations.esignmock.AutoOutcome;
import com.neobank.module.integrations.esignmock.EsignMode;
import com.neobank.module.integrations.esignmock.EsignProviderConfig;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The {@code PUT /esign/config} body. Only {@code mode} is required — the doc's own contract
 * example PUTs {@code {"mode":"SILENT","demoExpirySeconds":30}} with neither {@code delaySeconds}
 * nor {@code autoOutcome}, since SILENT needs neither. Omitted fields fall back to a fixed
 * default ({@link #toConfig()}) rather than merging with whatever the dials happened to be before
 * this call — the platform rule is "same request twice, same result once", and a merge would make
 * the result depend on server state the caller cannot see.
 */
public record EsignConfigCommand(
        @NotNull EsignMode mode,
        @PositiveOrZero Integer delaySeconds,
        AutoOutcome autoOutcome,
        @Positive Integer demoExpirySeconds) {

    public EsignProviderConfig toConfig() {
        return new EsignProviderConfig(
                mode,
                delaySeconds == null ? 0 : delaySeconds,
                autoOutcome == null ? AutoOutcome.SIGN : autoOutcome,
                demoExpirySeconds);
    }
}
