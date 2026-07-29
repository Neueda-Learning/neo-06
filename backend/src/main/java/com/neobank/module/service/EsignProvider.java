package com.neobank.module.service;

import com.neobank.module.dto.EsignConfigUpdate;
import com.neobank.module.dto.EsignConfigView;

/**
 * <h2>UC 07 · Operate Mock Control Panel — the seam that makes the e-sign provider swappable.</h2>
 *
 * <p>Everything that calls out for a signature — {@link ApplicationService}, and the admin panel's
 * controller — depends on THIS interface, never on {@link EsignMockService} directly. That is the
 * same shape as {@link com.neobank.module.integrations.orchestrator.OrchestratorClient}'s
 * {@code ORCHESTRATOR_URL} swap: one bean implements it today (the in-process mock); a real
 * e-sign provider integration would be a second implementation, selected by config, with no
 * caller ever changing.</p>
 */
public interface EsignProvider {

    /** The dials as they stand right now — always live, never cached. */
    EsignConfigView getConfig();

    /** Applies a partial update and returns the resulting, full dial state. */
    EsignConfigView updateConfig(EsignConfigUpdate update);

    /**
     * Registers a new envelope for signature and returns its id plus the dial snapshot taken at
     * registration time — see {@link EnvelopeRegistration}.
     */
    EnvelopeRegistration registerEnvelope(String applicationId, String documentSha,
            String signerName);

    /**
     * Plays the customer according to {@code registration}'s snapshot: {@code INSTANT}/{@code
     * DELAYED} eventually post a signature event back to THIS module's own {@code
     * /cases/{id}/signature-events} door; {@code SILENT} does nothing. Call this only AFTER the
     * case has actually been moved to {@code PENDING} and committed — see
     * {@link ApplicationService#decide} for why the ordering matters.
     */
    void playAutoMode(String applicationId, EnvelopeRegistration registration);
}
