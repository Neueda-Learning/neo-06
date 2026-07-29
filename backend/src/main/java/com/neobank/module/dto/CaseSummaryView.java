package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Instant;

/**
 * One row of {@code GET /cases} — see
 * {@code module-06-agreement-management-docs/uc-01-search-cases.md}'s Contract section.
 * {@code applicantName} is deliberately NOT here — the UI hydrates it live via
 * {@code GET /cases/{id}/applicant}, at most 10 calls per render.
 */
public record CaseSummaryView(
        String applicationId,
        String status,
        String termsVersion,
        Instant sentAt,
        Instant signedAt) {

    public static CaseSummaryView of(AgreementRecord record) {
        return new CaseSummaryView(
                record.getApplicationId(),
                record.getStatus().name(),
                record.getTermsVersion(),
                record.getSentAt(),
                record.getSignedAt());
    }
}
