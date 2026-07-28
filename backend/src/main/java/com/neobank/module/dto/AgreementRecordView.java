package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own read API, not the
 * orchestrator's. A view record, not the JPA entity: see {@code DemoShowcaseView}'s note on why
 * that boundary matters (it is the same reasoning here, just for the real table).
 *
 * <p>Deliberately minimal for UC 00 — {@code applicationId}, {@code status}, {@code createdAt}.
 * UC 01 (Search Cases) replaces this read path with its own {@code /cases} endpoint and richer
 * shape; this one stays only as long as {@code ApplicationController.list()} needs something to
 * return.</p>
 */
public record AgreementRecordView(
        String applicationId,
        String status,
        Instant createdAt) {

    public static AgreementRecordView of(AgreementRecord row) {
        return new AgreementRecordView(row.getApplicationId(), row.getStatus().name(), row.getCreatedAt());
    }
}
