package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * <h2>The timeline — one insert-only row per status transition. Added by UC 06.</h2>
 *
 * <p>Nothing here is ever updated or deleted: a case's history is its audit trail, including the
 * refusals — {@code SignatureEventService} writes a {@code REFUSED_LATE_EVENT} row even when a
 * signature event is rejected {@code 409} after the case has already expired (see the UC 06
 * brief's "Expected data changes": "refusals are audit too").</p>
 */
@Entity
@Table(name = "agreement_status_history")
public class AgreementStatusHistory {

    /** Surrogate key — same reasoning as the tiebreak columns elsewhere in this module: rows in
     *  the same second need a monotonic order, and {@code occurredAt} alone can't guarantee one. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The agreement whose transition this row records. */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** The status the case left — equal to {@code toStatus} on a refused, no-op transition. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private AgreementStatus fromStatus;

    /** The status the case entered. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private AgreementStatus toStatus;

    /** What caused the move, e.g. {@code SIGNATURE_EVENT}, {@code REFUSED_LATE_EVENT}. */
    @Column(nullable = false, length = 64)
    private String event;

    /** Who or what did it — the customer via the signing page, the system clock, an operator. */
    @Column(nullable = false, length = 64)
    private String actor;

    /** When this row was recorded — the detail screen renders the timeline verbatim. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AgreementStatusHistory() {
        // JPA
    }

    public AgreementStatusHistory(String applicationId, AgreementStatus fromStatus,
                                  AgreementStatus toStatus, String event, String actor,
                                  Instant occurredAt) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.event = event;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public AgreementStatus getFromStatus() {
        return fromStatus;
    }

    public AgreementStatus getToStatus() {
        return toStatus;
    }

    public String getEvent() {
        return event;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
