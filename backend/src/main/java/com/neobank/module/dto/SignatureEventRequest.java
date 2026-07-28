package com.neobank.module.dto;

import com.neobank.module.model.SignatureEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * What the signing page (or the e-sign mock in auto-mode) POSTs to
 * {@code /cases/{id}/signature-events} — UC 06's own contract, not the orchestrator's.
 *
 * <p>Unlike {@link com.neobank.module.integrations.orchestrator.Application}, this shape is one
 * this module defines itself, so it is typed strictly: an {@code event} outside
 * {@link SignatureEventType} — most importantly {@code "EXPIRED"}, which belongs to this
 * module's own clock, never to a caller — is refused {@code 400} by Jackson before the service
 * ever sees it (AC 8).</p>
 *
 * @param envelopeId the envelope this event claims to be for; matched against the case's CURRENT
 *                   envelope so a stale one (rotated away by a resend) is refused, not applied
 * @param event      {@code SIGNED} or {@code DECLINED} — the two facts a customer can report
 * @param occurredAt when the customer actually acted, stamped onto {@code signedAt} for SIGNED
 */
public record SignatureEventRequest(
        @NotBlank String envelopeId,
        @NotNull SignatureEventType event,
        @NotNull Instant occurredAt) {
}
