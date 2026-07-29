package com.neobank.module.service;

import com.neobank.module.integrations.esignmock.EsignMockClient;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * (Re)sends a case for signature: registers a fresh envelope and computes the clock it starts —
 * the one piece of logic UC 00's initial send and UC 04's resend both need, so neither
 * re-implements it.
 *
 * <p>The expiry window itself comes from {@link EsignMockClient#registerEnvelope} — UC 07's
 * {@code demoExpirySeconds} dial overrides the default there for the next envelope only (its own
 * class javadoc has the detail); this class just adds that window to "now" to get
 * {@code expiresAt}, keeping that column's ownership on the {@code AgreementRecord} side where the
 * entity model already puts it.</p>
 */
@Service
public class EnvelopeService {

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
        EsignMockClient.EnvelopeRegistration registration = esignMock.registerEnvelope(applicationId);
        Instant sentAt = Instant.now();
        Instant expiresAt = sentAt.plusSeconds(registration.expirySeconds());
        return new Registration(registration.envelopeId(), sentAt, expiresAt);
    }

    /** A freshly registered envelope and the clock it starts. */
    public record Registration(String envelopeId, Instant sentAt, Instant expiresAt) {
    }
}

