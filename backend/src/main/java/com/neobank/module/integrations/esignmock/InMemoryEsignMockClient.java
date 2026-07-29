package com.neobank.module.integrations.esignmock;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.service.SignatureEventService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * UC 07 · Operate Mock Control Panel — the mock's actual behaviour, dial-driven. See the package
 * javadoc for why this plays the customer in-process rather than over a second HTTP hop.
 *
 * <p>{@link #registerEnvelope} does three things, all synchronously except the scheduled post
 * itself: mint an id, snapshot the CURRENT dials (AC6 — a later dial change must never touch an
 * envelope already registered), and — for {@link EsignMode#INSTANT}/{@link EsignMode#DELAYED} —
 * schedule the auto-outcome to post back after the right delay. {@link EsignMode#SILENT} schedules
 * nothing: "the clock will decide" ({@link com.neobank.module.service.ExpiryClockService}).</p>
 */
@Component
public class InMemoryEsignMockClient implements EsignMockClient {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEsignMockClient.class);

    /**
     * {@code AgreementConfig.expiryDays}' documented Day-0 seed value (5 days), used whenever the
     * panel's {@code demoExpirySeconds} dial is unset — same gap already noted in
     * {@code AgreementDocumentComposer}'s javadoc for the min-payment constants: move this to
     * {@code AgreementConfig} once that table exists.
     */
    static final long DEFAULT_EXPIRY_SECONDS = 5 * 24 * 3600L;

    private final EsignProviderConfigService config;
    private final SignatureEventService signatureEvents;
    private final TaskScheduler scheduler;

    public InMemoryEsignMockClient(EsignProviderConfigService config,
            SignatureEventService signatureEvents, TaskScheduler scheduler) {
        this.config = config;
        this.signatureEvents = signatureEvents;
        this.scheduler = scheduler;
    }

    @Override
    public EnvelopeRegistration registerEnvelope(String applicationId) {
        String envelopeId = "env-" + UUID.randomUUID().toString().substring(0, 8);

        // Snapshot NOW: AC6 says dial changes apply to the NEXT envelope only — reading
        // config.get() again later (e.g. inside the scheduled task) could pick up a change made
        // in between and misplay an envelope that had already been handed its own dials.
        EsignProviderConfig dials = config.get();
        long expirySeconds = dials.demoExpirySeconds() != null
                ? dials.demoExpirySeconds()
                : DEFAULT_EXPIRY_SECONDS;

        if (dials.mode() != EsignMode.SILENT) {
            // INSTANT still needs a small floor, not a literal 0: this method runs synchronously,
            // INSIDE the caller's still-in-progress save (EnvelopeService.register() -> its own
            // caller stamps envelopeId onto the AgreementRecord and saves it AFTER this method
            // returns). A 0-delay schedule can fire on the scheduler's own thread before that save
            // has happened, and SignatureEventService then refuses it as a stale envelope — a real
            // race, caught in manual testing, not a hypothetical one. AC2 only promises "within
            // seconds", so a 1s floor costs nothing observable while leaving a comfortable margin.
            long delaySeconds = dials.mode() == EsignMode.DELAYED
                    ? Math.max(dials.delaySeconds(), 0)
                    : 1;
            SignatureEventType outcome = dials.autoOutcome().toSignatureEventType();
            scheduler.schedule(() -> playCustomer(applicationId, envelopeId, outcome),
                    Instant.now().plusSeconds(delaySeconds));
        }
        // SILENT: post nothing — the case stays PENDING until ExpiryClockService's sweep decides.

        return new EnvelopeRegistration(envelopeId, expirySeconds);
    }

    /**
     * Plays the customer: posts to the exact door {@code SignatureEventController} exposes for
     * everyone else (the signing page, a manual curl) — see UC 06. An in-process call rather than
     * a real HTTP round-trip, per this package's javadoc; the effect on the stored
     * {@link com.neobank.module.model.AgreementRecord} is identical either way.
     */
    private void playCustomer(String applicationId, String envelopeId, SignatureEventType outcome) {
        try {
            signatureEvents.apply(applicationId,
                    new SignatureEventRequest(envelopeId, outcome, Instant.now()));
        } catch (RuntimeException e) {
            // Not fatal: e.g. a resend rotated the envelope, or the expiry clock got there first,
            // before this timer fired. SignatureEventService's own guards already refuse a stale
            // or out-of-turn event; there is nothing more for the mock to do but note it.
            log.info("auto-{} for {} envelope {} did not apply: {}", outcome, applicationId,
                    envelopeId, e.toString());
        }
    }
}

