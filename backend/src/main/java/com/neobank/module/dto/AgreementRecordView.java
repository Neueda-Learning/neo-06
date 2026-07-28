package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own board, not the orchestrator's.
 *
 * <p>Deliberately thin: the surrogate/entity details of {@link AgreementRecord} stay behind this
 * boundary, and the board only ever needs to know what case exists, what state it is in, and when
 * this module first saw it.</p>
 */
public record AgreementRecordView(
        String applicationId,
        String status,
        Instant createdAt) {

    public static AgreementRecordView of(AgreementRecord row) {
        return new AgreementRecordView(row.getApplicationId(), row.getStatus().name(), row.getCreatedAt());
    }
}
