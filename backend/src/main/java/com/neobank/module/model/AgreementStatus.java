package com.neobank.module.model;

/**
 * The signature case's own lifecycle — module 6's internal domain, distinct from the three-value
 * {@link Decision} this module reports to the orchestrator.
 *
 * <p>{@link #GENERATING} is where every case starts and is never reported on the wire — the
 * orchestrator only ever sees {@link Decision#ACCEPTED}, {@link Decision#REJECTED} or
 * {@link Decision#REFERRED}. The rest of this enum ({@link #PENDING}, {@link #SIGNED},
 * {@link #EXPIRED}) belongs to the decision engine that runs after UC 00's row exists — see the
 * use-case brief's state diagram; UC 00 itself only ever writes {@link #GENERATING} and, on the
 * consent gate, {@link #DECLINED}.</p>
 */
public enum AgreementStatus {

    /** Internal only — the row UC 00 inserts before the ack. No callback ever names this. */
    GENERATING,

    /** Sent for signature; not yet decided by the customer. Written by a later use case. */
    PENDING,

    /** The customer signed. Terminal. Written by a later use case. */
    SIGNED,

    /** Consent gate failed (or a later override/decline). Terminal from here. */
    DECLINED,

    /** The signing window closed unsigned. Written by a later use case (the expiry clock). */
    EXPIRED
}
