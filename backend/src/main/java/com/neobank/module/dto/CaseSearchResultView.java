package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Instant;

/**
 * <h2>UC 01 · Search Cases — one row of the {@code GET /api/v1/cases} response.</h2>
 *
 * <p>The shape is fixed by the UC 01 contract: {@code applicationId}, {@code status},
 * {@code termsVersion}, {@code sentAt}, {@code signedAt}. No {@code applicantName} — the
 * schema has no name column (and never will); the UI hydrates ≤10 visible rows live via the
 * module's application-fetch GET, never this payload.</p>
 *
 * <p><b>The three "richer" fields are nullable on purpose.</b> {@code termsVersion},
 * {@code sentAt} and {@code signedAt} belong to later use cases (review, document, signature
 * events) that grow {@link AgreementRecord} with their own columns. UC 01 returns them as
 * {@code null} until those use cases land — the contract shape is honoured, the columns are
 * not guessed ahead of time (see {@code AgreementRecord}'s javadoc on growing columns).</p>
 *
 * <p>{@code createdAt} is deliberately NOT in the contract row even though the entity has it:
 * the board's "newest first" ordering is the query's job, not the payload's. The existing
 * {@link AgreementRecordView} stays for the legacy {@code GET /api/v1/applications} list —
 * this view is the UC 01 replacement, not an edit of that one.</p>
 */
public record CaseSearchResultView(
        String applicationId,
        String status,
        String termsVersion,
        Instant sentAt,
        Instant signedAt) {

    /** Map a row to the contract shape. The three later-use-case fields are null until they land. */
    public static CaseSearchResultView of(AgreementRecord row) {
        return new CaseSearchResultView(
                row.getApplicationId(),
                row.getStatus().name(),
                null,   // termsVersion — added by UC 02 Review
                null,   // sentAt — added by the use case that sends for signature
                null);  // signedAt — added by UC 06 Signature Events
    }
}
