package com.neobank.module.model;

/**
 * What the customer did, as posted to {@code POST /cases/{id}/signature-events} — UC 06's whole
 * vocabulary.
 *
 * <p>Deliberately an enum, unlike the codes in
 * {@link com.neobank.module.integrations.orchestrator.Application} — this event is a contract
 * this module itself defines (see the use-case brief's AC 8), so an unknown value, e.g.
 * {@code "EXPIRED"}, must be refused {@code 400} rather than swallowed: the expiry clock is this
 * module's own, never the signing page's to report.</p>
 */
public enum SignatureEventType {

    /** The customer signed. Moves a PENDING case to {@link AgreementStatus#SIGNED} — terminal. */
    SIGNED,

    /** The customer declined. Moves a PENDING case to {@link AgreementStatus#DECLINED} — terminal. */
    DECLINED
}
