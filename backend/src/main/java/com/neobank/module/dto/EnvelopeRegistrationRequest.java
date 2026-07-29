package com.neobank.module.dto;

/**
 * The mock's own API — {@code POST /mock/esign/envelopes} — request body, mirroring what a real
 * e-sign provider's registration call would take: {@code {applicationId, documentSha,
 * signerName}}. See the UC 07 brief's Build notes.
 */
public record EnvelopeRegistrationRequest(
        String applicationId,
        String documentSha,
        String signerName) {
}
