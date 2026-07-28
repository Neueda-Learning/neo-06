package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * <h2>The signature case — one row per {@code applicationId}, from {@code GENERATING} to a
 * terminal state.</h2>
 *
 * <p>This is module 6's real table (UC 00 · Process Application). {@code applicationId} — the
 * envelope's id, never the copy inside {@code application} — is both the primary key and the ONLY
 * applicant-related data this schema ever stores: the payload itself is never persisted, only
 * handed to the off-thread worker.</p>
 *
 * <p>Later use cases (review, document, queue, signature events, override) extend this row with
 * more columns (reference, envelopeId, termsVersion, approvedLimit, apr, …) as they land — see
 * {@code module-06-agreement-management-docs/uc-00-process-application.md}'s suggested entity
 * model for the full shape. UC 00 only needs {@code status}, so only that is added here; growing
 * columns is a job for the change set that first needs them, not a guess made ahead of time.</p>
 */
@Entity
@Table(name = "agreement_record")
public class AgreementRecord {

    /** The journey key from the envelope — unique, and the ONLY applicant-related column here. */
    @Id
    @Column(name = "application_id", length = 64, nullable = false, updatable = false)
    private String applicationId;

    /** GENERATING, PENDING, SIGNED, DECLINED or EXPIRED — GENERATING is internal. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgreementStatus status;

    /** When the row was inserted — the hand-off point between the request thread and the worker. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the status last changed. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgreementRecord() {
        // JPA
    }

    public AgreementRecord(String applicationId, AgreementStatus status) {
        this.applicationId = applicationId;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** The only mutation UC 00 performs post-insert: the consent-gate move to DECLINED. */
    public void changeStatus(AgreementStatus newStatus) {
        this.status = newStatus;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public AgreementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
