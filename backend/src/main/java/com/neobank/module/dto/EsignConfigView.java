package com.neobank.module.dto;

import com.neobank.module.integrations.esignmock.AutoOutcome;
import com.neobank.module.integrations.esignmock.EsignMode;
import com.neobank.module.integrations.esignmock.EsignProviderConfig;

/**
 * What {@code GET}/{@code PUT} {@code /esign/config} return — see
 * {@code module-06-agreement-management-docs/uc-07-operate-mock-control-panel.md}'s "Contract".
 */
public record EsignConfigView(
        EsignMode mode,
        int delaySeconds,
        AutoOutcome autoOutcome,
        Integer demoExpirySeconds) {

    public static EsignConfigView of(EsignProviderConfig config) {
        return new EsignConfigView(config.mode(), config.delaySeconds(), config.autoOutcome(),
                config.demoExpirySeconds());
    }
}
