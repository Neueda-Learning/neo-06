package com.neobank.module.controller;

import com.neobank.module.dto.EsignConfigCommand;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.integrations.esignmock.EsignProviderConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 07 · Operate Mock Control Panel — {@code GET}/{@code PUT} {@code /esign/config}. See
 * {@code docs/uc-07-operate-mock-control-panel.md}.
 *
 * <p>Always {@code 200}: the dials live in this module's own process (see
 * {@code integrations.esignmock}'s package javadoc), so unlike UC 03's applicant proxy there is no
 * downstream network call here that could fail — nothing to degrade, nothing to report as
 * unreachable.</p>
 */
@RestController
@RequestMapping("/esign/config")
public class EsignConfigController {

    private final EsignProviderConfigService config;

    public EsignConfigController(EsignProviderConfigService config) {
        this.config = config;
    }

    @GetMapping
    public EsignConfigView get() {
        return EsignConfigView.of(config.get());
    }

    /**
     * AC6: applies live, to the NEXT envelope only — no restart, and nothing already in flight is
     * rewritten (see {@link com.neobank.module.integrations.esignmock.InMemoryEsignMockClient}).
     */
    @PutMapping
    public EsignConfigView put(@Valid @RequestBody EsignConfigCommand command) {
        return EsignConfigView.of(config.apply(command.toConfig()));
    }
}
