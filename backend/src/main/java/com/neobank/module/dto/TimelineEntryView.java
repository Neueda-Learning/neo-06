package com.neobank.module.dto;

import com.neobank.module.model.AgreementStatusHistory;
import java.time.Instant;

/** One row of {@code CaseDetailView.timeline}, oldest first — see UC02's contract. */
public record TimelineEntryView(
        String fromStatus,
        String toStatus,
        String event,
        String actor,
        Instant occurredAt) {

    public static TimelineEntryView of(AgreementStatusHistory row) {
        return new TimelineEntryView(
                row.getFromStatus().name(),
                row.getToStatus().name(),
                row.getEvent(),
                row.getActor(),
                row.getOccurredAt());
    }
}
