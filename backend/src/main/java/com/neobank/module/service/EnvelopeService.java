package com.neobank.module.service;

import com.neobank.module.integrations.esignmock.EsignMockClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/**
 * (Re)sends a case for signature: registers a fresh envelope and computes the clock it starts —
 * the one piece of logic UC 00's initial send and UC 04's resend both need, so neither
 * re-implements it.
 *
 * <h2>⚠️ Blocked on the same missing prerequisite as UC05 — flagged, not worked around</h2>
 *
 * <p>{@code expiresAt} is supposed to be {@code sentAt + AgreementConfig.expiryDays} (see
 * {@code prompts/uc-04-prompt.md}), but there is no {@code AgreementConfig} entity, table or Day-0
 * seed data anywhere in this repository (same gap already noted in
 * {@link AgreementDocumentComposer}'s javadoc for {@code minPaymentPct}/{@code minPaymentFloorGbp}).
 * {@link #EXPIRY_DAYS} is the UC05/UC04 briefs' own documented Day-0 seed value (5), inlined here
 * only so the queue/resend flow has real clock data to test against — move it to
 * {@code AgreementConfig} once that table exists, the same note left on the min-payment
 * constants.</p>
 */
@Service
public class EnvelopeService {

    /** {@code AgreementConfig.expiryDays}'s documented Day-0 seed value — see class javadoc. */
    static final int EXPIRY_DAYS = 5;

    private final EsignMockClient esignMock;

    public EnvelopeService(EsignMockClient esignMock) {
        this.esignMock = esignMock;
    }

    /**
     * Registers a fresh envelope for {@code applicationId} and computes the expiry clock from
     * now. Does not persist anything — callers apply the result to their own
     * {@link com.neobank.module.model.AgreementRecord} via
     * {@link com.neobank.module.model.AgreementRecord#sendForSignature}.
     */
    public Registration register(String applicationId) {
        String envelopeId = esignMock.registerEnvelope(applicationId);
        Instant sentAt = Instant.now();
        Instant expiresAt = sentAt.plus(EXPIRY_DAYS, ChronoUnit.DAYS);
        return new Registration(envelopeId, sentAt, expiresAt);
    }

    /** A freshly registered envelope and the clock it starts. */
    public record Registration(String envelopeId, Instant sentAt, Instant expiresAt) {
    }
}
