package com.neobank.module.dto;

import com.neobank.module.model.AgreementRecord;
import java.time.Duration;
import java.time.Instant;

/**
 * One row of {@code GET /queue} — see
 * {@code module-06-agreement-management-docs/uc-04-pending-expired-queue.md}'s Contract section.
 */
public record QueueEntryView(
        String applicationId,
        String state,
        Instant sentAt,
        Instant expiresAt,
        long envelopeCount,
        long ageHours) {

    public static QueueEntryView of(AgreementRecord record, long envelopeCount, Instant now) {
        Instant since = record.getSentAt() != null ? record.getSentAt() : record.getCreatedAt();
        long ageHours = since == null ? 0 : Duration.between(since, now).toHours();
        return new QueueEntryView(
                record.getApplicationId(),
                record.getStatus().name(),
                record.getSentAt(),
                record.getExpiresAt(),
                envelopeCount,
                ageHours);
    }
}
