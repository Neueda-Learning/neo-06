package com.neobank.module.integrations.esignmock;

/**
 * Registers an envelope with the e-sign provider — called whenever a case is (re)sent for
 * signature: UC 00's initial send and UC 04's resend both go through this one seam. See the
 * package javadoc for why {@link InMemoryEsignMockClient} is the only implementation today.
 */
public interface EsignMockClient {

    /**
     * @param applicationId the case being (re)sent — real providers would need it to correlate
     *                      their own callback; the in-memory stub uses it to know which case to
     *                      post the auto-outcome back to
     * @return a fresh envelope id, e.g. {@code "env-8f14e45f"} (never the same value twice), and
     *         the expiry window (in seconds) this envelope should give the case — UC 07's
     *         {@code demoExpirySeconds} dial if set, otherwise the default seed
     */
    EnvelopeRegistration registerEnvelope(String applicationId);

    /**
     * @param envelopeId    the fresh envelope id
     * @param expirySeconds how long, from now, this envelope's case may stay PENDING before the
     *                      expiry clock takes it — the caller ({@link
     *                      com.neobank.module.service.EnvelopeService}) adds this to its own
     *                      {@code sentAt} to get {@code expiresAt}, keeping that column's ownership
     *                      exactly where the entity model already puts it
     */
    record EnvelopeRegistration(String envelopeId, long expirySeconds) {
    }
}

