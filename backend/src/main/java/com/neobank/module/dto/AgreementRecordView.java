package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own board, not the orchestrator's.
 *
 * <p>Deliberately thin: the surrogate/entity details of {@link AgreementRecord} stay behind this
 * boundary. UC 01 (Search Cases) replaces this read path with its own {@code /cases} endpoint and
 * a richer shape; this one stays only as long as {@code ApplicationController.list()} needs
 * something to return.</p>
 */
public record AgreementRecordView(
        String applicationId,
        String status,
        Instant createdAt) {

    public static AgreementRecordView of(AgreementRecord row) {
        return new AgreementRecordView(row.getApplicationId(), row.getStatus().name(), row.getCreatedAt());
    }
}
