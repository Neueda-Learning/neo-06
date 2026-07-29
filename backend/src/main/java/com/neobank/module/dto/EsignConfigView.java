package com.neobank.module.dto;

import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;
import com.neobank.module.model.EsignProviderConfig;

/**
 * {@code GET}/{@code PUT /esign/config}'s response shape — see the UC 07 brief's Contract
 * section. Matches it field for field: {@code mode}, {@code delaySeconds}, {@code autoOutcome},
 * {@code demoExpirySeconds}.
 */
public record EsignConfigView(
        EsignMode mode,
        int delaySeconds,
        EsignOutcome autoOutcome,
        Integer demoExpirySeconds) {

    public static EsignConfigView of(EsignProviderConfig config) {
        return new EsignConfigView(config.getMode(), config.getDelaySeconds(),
                config.getAutoOutcome(), config.getDemoExpirySeconds());
    }
}
