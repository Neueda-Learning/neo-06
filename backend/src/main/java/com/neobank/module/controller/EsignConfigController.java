package com.neobank.module.controller;

import com.neobank.module.dto.EsignConfigUpdate;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.service.EsignProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 07 · Operate Mock Control Panel — {@code GET}/{@code PUT /esign/config}, exactly the paths
 * in the brief's Contract section (not under {@code /api/v1}, the fixed orchestrator family, nor
 * {@code /cases}, UC 02/06/08's family — this is its own thing, the operator's dial panel).
 *
 * <p>"Proxy to the mock" in the brief means an HTTP hop to a separate container in the full
 * system; here it is a direct call into {@link EsignProvider} because the mock is embedded (see
 * {@code EsignMockService}'s class Javadoc) — same seam, one hop shorter.</p>
 */
@RestController
public class EsignConfigController {

    private final EsignProvider esignProvider;

    public EsignConfigController(EsignProvider esignProvider) {
        this.esignProvider = esignProvider;
    }

    @GetMapping("/esign/config")
    public EsignConfigView getConfig() {
        return esignProvider.getConfig();
    }

    /** AC 6: applies live, to the NEXT envelope — nothing here restarts anything. */
    @PutMapping("/esign/config")
    public EsignConfigView updateConfig(@RequestBody EsignConfigUpdate update) {
        return esignProvider.updateConfig(update);
    }
}
