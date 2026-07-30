package com.neobank.module.model;

/**
 * What this module may tell the orchestrator about an application.
 *
 * <p>Three of these are outcomes and one is not. Only {@link #ACCEPTED} advances the journey;
 * {@link #REJECTED} ends it and {@link #REFERRED} parks it for a human. {@link #PENDING} does
 * none of those — it is this module saying it has started and has not finished. The names are the
 * exact strings the orchestrator expects on the callback, which is why this is an enum and not a
 * {@code String}: a typo cannot reach the wire.</p>
 */
public enum Decision {

    /** Your step passed. The only outcome that moves the application to the next module. */
    ACCEPTED,

    /** A rule failed and no human can rescue it. Ends the journey. */
    REJECTED,

    /** You cannot decide automatically. Parks the application for a human. */
    REFERRED,

    /**
     * Not an outcome: the agreement has been sent and we are waiting for the customer to sign it.
     *
     * <p>The journey neither advances nor ends — it holds on this step until a second report
     * carries the real answer ({@code ACCEPTED} when they sign, {@code REJECTED} when they
     * decline, {@code REFERRED} when the envelope expires). Reporting {@code ACCEPTED} at
     * send-for-signature time instead would move the journey on to the next module, and this
     * module's actual answer would then arrive at a step that no longer belongs to it and be
     * discarded — which is exactly what happened before this constant existed.</p>
     *
     * <p>The orchestrator gives a signature wait its own, far longer timeout; see
     * {@code orchestrator.signature.timeout}.</p>
     */
    PENDING
}
