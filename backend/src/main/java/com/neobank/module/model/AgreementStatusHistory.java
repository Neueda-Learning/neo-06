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
 * <h2>The timeline — one insert-only row per status transition, never deleted.</h2>
 *
 * <p>UC02's "Agreement Detail" screen renders this verbatim, oldest first. Nothing in this module
 * yet writes these rows (the lifecycle transitions are out of scope for UC00/UC02 — a later use
 * case, e.g. sending the envelope or receiving a signature event, is the writer); this entity and
 * its repository exist so UC02 has something real to read and to seed fixtures against.</p>
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

    /** The status the case left. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private AgreementStatus fromStatus;

    /** The status the case entered. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private AgreementStatus toStatus;

    /** What caused the move — e.g. {@code ENVELOPE_SENT}, {@code SIGNATURE_EVENT}, {@code CONSENT_GATE}. */
    @Column(nullable = false, length = 64)
    private String event;

    /** Who or what did it — the customer, the system clock, or a named operator. */
    @Column(nullable = false, length = 64)
    private String actor;

    /** When the transition happened. */
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
