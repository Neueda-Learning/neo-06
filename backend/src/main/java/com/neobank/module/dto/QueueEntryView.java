package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Duration;
import java.time.Instant;

/**
 * One row of {@code GET /queue?state=PENDING|EXPIRED} — see
 * {@code docs/uc-04-pending-expired-queue.md}'s "Contract" section.
 *
 * <p>{@code envelopeCount} is derived from {@code AgreementStatusHistory} at read time, never
 * stored (the brief's own "从历史行动态统计得出，不额外存储字段") — see
 * {@code AgreementStatusHistoryRepository#countByApplicationIdAndToStatus}. {@code ageHours} is
 * likewise computed here, off {@code sentAt} and "now".</p>
 */
public record QueueEntryView(
        String applicationId,
        String state,
        Instant sentAt,
        Instant expiresAt,
        long envelopeCount,
        long ageHours) {

    public static QueueEntryView of(AgreementRecord record, long envelopeCount) {
        Instant sentAt = record.getSentAt();
        long ageHours = sentAt == null ? 0 : Duration.between(sentAt, Instant.now()).toHours();
        return new QueueEntryView(
                record.getApplicationId(),
                record.getStatus().name(),
                sentAt,
                record.getExpiresAt(),
                envelopeCount,
                ageHours);
    }
}
