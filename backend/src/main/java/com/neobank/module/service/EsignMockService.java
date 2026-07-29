package com.neobank.module.service;

import com.neobank.module.dto.EsignConfigUpdate;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;
import com.neobank.module.model.EsignProviderConfig;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.repository.EsignProviderConfigRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 07 · Operate Mock Control Panel.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-07-operate-mock-control-panel.md}: the
 * brief's own build shape is TWO apps — the e-sign mock as its own service and container, and
 * this module's panel proxying {@code GET}/{@code PUT /esign/config} to it.</p>
 *
 * <h3>Why this is one class instead of two apps</h3>
 *
 * <p>Standing up a second container is an infra change (a new {@code docker-compose.yml} service,
 * a new image to build and deploy) that this branch is explicitly not allowed to make. So the
 * mock lives IN this module instead, behind the {@link EsignProvider} seam — every caller
 * ({@link ApplicationService}, {@code EsignConfigController}) depends on that interface, never on
 * this class, so replacing this with a real separate service later is a new implementation and a
 * bean swap, not a rewrite of anyone who calls it. That is the "pluggable part" the brief's own
 * admin-API-proxy shape was already pointing at.</p>
 *
 * <p>One consequence worth naming: AC 7 ("mock unreachable at the send leg") cannot literally
 * happen here — an in-process method call cannot time out the way a real HTTP call to a separate
 * container could. {@link ApplicationService#decide} still defends against this class throwing
 * (any {@link RuntimeException} is treated the same way a network failure would be), which is as
 * much of AC 7 as an embedded mock can honestly prove.</p>
 */
@Service
public class EsignMockService implements EsignProvider {

    private static final Logger log = LoggerFactory.getLogger(EsignMockService.class);

    private final EsignProviderConfigRepository configs;
    private final SignatureEventService signatureEvents;
    private final EsignEventScheduler scheduler;

    public EsignMockService(EsignProviderConfigRepository configs,
                            SignatureEventService signatureEvents,
                            EsignEventScheduler scheduler) {
        this.configs = configs;
        this.signatureEvents = signatureEvents;
        this.scheduler = scheduler;
    }

    @Override
    @Transactional
    public EsignConfigView getConfig() {
        return EsignConfigView.of(currentConfig());
    }

    @Override
    @Transactional
    public EsignConfigView updateConfig(EsignConfigUpdate update) {
        EsignProviderConfig config = currentConfig();
        config.applyUpdate(update.mode(), update.delaySeconds(), update.autoOutcome(),
                update.demoExpirySeconds());
        configs.save(config);
        log.info("e-sign mock dials updated: mode={} delaySeconds={} autoOutcome={} "
                + "demoExpirySeconds={} — applies to the NEXT envelope",
                config.getMode(), config.getDelaySeconds(), config.getAutoOutcome(),
                config.getDemoExpirySeconds());
        return EsignConfigView.of(config);
    }

    /**
     * AC 1: {@code POST /envelopes → envelopeId}. Takes a snapshot of the CURRENT dials so a
     * later dial change cannot retroactively change how this envelope plays (AC 6).
     */
    @Override
    @Transactional
    public EnvelopeRegistration registerEnvelope(String applicationId, String documentSha,
            String signerName) {
        String envelopeId = "env-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        EsignConfigView snapshot = EsignConfigView.of(currentConfig());
        log.info("REGISTERED envelope {} for {} (signer={}, mode={})", envelopeId, applicationId,
                signerName, snapshot.mode());
        return new EnvelopeRegistration(envelopeId, snapshot);
    }

    /**
     * AC 2/3/4: {@code INSTANT} and {@code DELAYED} post the SAME {@code /signature-events} door
     * a real signing page would — see {@link SignatureEventService#apply} — just called directly
     * rather than over HTTP to itself. {@code SILENT} (AC 5) posts nothing at all; the case's
     * {@code expiresAt} (stamped by {@link ApplicationService} from this registration's
     * {@code demoExpirySeconds}) is what ends it later.
     */
    @Override
    public void playAutoMode(String applicationId, EnvelopeRegistration registration) {
        EsignConfigView config = registration.config();
        if (config.mode() == EsignMode.SILENT) {
            log.info("SILENT — {} awaits its expiry clock, nothing posted", applicationId);
            return;
        }
        SignatureEventType event = config.autoOutcome() == EsignOutcome.SIGN
                ? SignatureEventType.SIGNED
                : SignatureEventType.DECLINED;
        if (config.mode() == EsignMode.INSTANT) {
            // AC 2: "reaches SIGNED within seconds" — there is no reason to hop onto the
            // background scheduler thread for a zero delay; firing inline keeps INSTANT
            // deterministic (no race against whoever reads the case right after the 202).
            fireSignatureEvent(applicationId, registration.envelopeId(), event);
            return;
        }
        scheduler.schedule(() -> fireSignatureEvent(applicationId, registration.envelopeId(), event),
                config.delaySeconds());
    }

    private void fireSignatureEvent(String applicationId, String envelopeId,
            SignatureEventType event) {
        try {
            signatureEvents.apply(applicationId,
                    new SignatureEventRequest(envelopeId, event, Instant.now()));
        } catch (RuntimeException e) {
            // The case may have moved on (an override, a resend rotating the envelope) between
            // this being scheduled and it firing — log and drop rather than crash a daemon thread.
            log.warn("e-sign mock's auto-{} for {} (envelope {}) could not be applied: {}",
                    event, applicationId, envelopeId, e.getMessage());
        }
    }

    /** Seeded by Liquibase (see {@code 006-create-esign-provider-config.yaml}) — never absent. */
    private EsignProviderConfig currentConfig() {
        return configs.findById(EsignProviderConfig.SINGLETON_ID)
                .orElseGet(() -> configs.save(new EsignProviderConfig(
                        EsignMode.INSTANT, 0, EsignOutcome.SIGN, null)));
    }
}
