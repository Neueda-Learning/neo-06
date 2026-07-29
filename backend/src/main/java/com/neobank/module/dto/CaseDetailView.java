package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What {@code GET /cases/{applicationId}} returns — see
 * {@code module-06-agreement-management-docs/uc-02-review-agreement.md}'s "Contract" section.
 *
 * <p>Every field here is read straight off the stored {@link AgreementRecord} row — nothing is
 * computed or re-derived from the current {@code AgreementConfig} (AC6: "the STORED ones").
 * Fields that are {@code null} until a later lifecycle step (e.g. {@code envelopeId}/{@code sentAt}
 * on a consent-gate {@code DECLINED} case, AC5) serialise as JSON {@code null}, not omitted.</p>
 */
public record CaseDetailView(
        String status,
        String reference,
        String termsVersion,
        Integer approvedLimit,
        BigDecimal apr,
        Integer minPaymentGbp,
        String envelopeId,
        Instant sentAt,
        Instant expiresAt,
        Instant signedAt,
        List<TimelineEntryView> timeline,
        boolean documentAvailable) {

    public static CaseDetailView of(AgreementRecord record, List<TimelineEntryView> timeline,
            boolean documentAvailable) {
        return new CaseDetailView(
                record.getStatus().name(),
                record.getReference(),
                record.getTermsVersion(),
                record.getApprovedLimit(),
                record.getApr(),
                record.getMinPaymentGbp(),
                record.getEnvelopeId(),
                record.getSentAt(),
                record.getExpiresAt(),
                record.getSignedAt(),
                timeline,
                documentAvailable);
    }
}
