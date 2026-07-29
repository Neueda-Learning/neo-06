package com.neobank.module.integrations.esignmock;

/**
 * Registers an envelope with the e-sign provider — called whenever a case is (re)sent for
 * signature: UC 00's initial send and UC 04's resend both go through this one seam. See the
 * package javadoc for why {@link InMemoryEsignMockClient} is the only implementation today.
 */
public interface EsignMockClient {

    /**
     * @param applicationId the case being (re)sent — real providers would need it to correlate
     *                      their own callback; the in-memory stub ignores it
     * @return a fresh envelope id, e.g. {@code "env-8f14e45f"} — never the same value twice
     */
    String registerEnvelope(String applicationId);
}
