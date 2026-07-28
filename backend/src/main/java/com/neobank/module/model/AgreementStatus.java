package com.neobank.module.model;

/**
 * The signature case's own lifecycle — distinct from the three-value {@link Decision} this module
 * reports to the orchestrator. See
 * {@code module-06-agreement-management-docs/uc-00-process-application.md}'s state diagram.
 *
 * <p>{@link #GENERATING} is internal only: no callback ever names it, but operators must still see
 * it on the board (UC 01 AC6). UC00 itself only ever writes {@link #GENERATING} and, on the
 * consent gate, {@link #DECLINED} — {@link #PENDING}, {@link #SIGNED} and {@link #EXPIRED} are
 * written by later use cases (the decision engine, signature events, the expiry clock).</p>
 */
public enum AgreementStatus {

    /** Row just created by {@code /execute}; nothing decided yet. No callback ever names this. */
    GENERATING,

    /** Envelope sent, waiting on the customer. Written by a later use case. */
    PENDING,

    /** Terminal — the contract is in force on the customer's signature alone. Written later. */
    SIGNED,

    /** Terminal (until an override revives it) — consent gate, decline event, or abandon override. */
    DECLINED,

    /** Pending past the expiry window: the clock decided, not a person. Written by a later use case. */
    EXPIRED
}
