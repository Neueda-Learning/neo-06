package com.neobank.module.model;

/**
 * UC 07 — which {@code SignatureEventType} the e-sign mock posts in {@code INSTANT}/{@code
 * DELAYED} modes. Kept separate from {@link SignatureEventType} on purpose: this is a DIAL an
 * operator sets ahead of time, not an event a caller reports after the fact.
 */
public enum EsignOutcome {
    SIGN,
    DECLINE
}
