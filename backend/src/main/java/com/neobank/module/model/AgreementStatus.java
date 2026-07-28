package com.neobank.module.model;

/**
 * The signature case's lifecycle — see
 * {@code module-06-agreement-management-docs/uc-00-process-application.md}'s state diagram.
 *
 * <p>{@link #GENERATING} is internal: the orchestrator callback enum never names it, but operators
 * must still see it on the board (UC 01 AC6). The other four are the case's terminal-or-waiting
 * states once the (not-yet-built) lifecycle use cases start moving it.</p>
 */
public enum AgreementStatus {

    /** Row just created by {@code /execute}; nothing decided yet. */
    GENERATING,

    /** Envelope sent, waiting on the customer. */
    PENDING,

    /** Terminal — the contract is in force on the customer's signature alone. */
    SIGNED,

    /** Terminal (until an override revives it) — consent gate, decline event, or abandon override. */
    DECLINED,

    /** Pending past the expiry window: the clock decided, not a person. */
    EXPIRED
}
