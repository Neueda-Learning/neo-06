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

    /**
     * The CURRENT envelope registered with the e-sign mock, e.g. {@code env-8f14e45f} — added by
     * UC 06 (Receive Signature Events). Null until the decision engine (UC 05, not yet built)
     * sends the case for signature; a resend rotates it, and the old envelope's late events are
     * refused by {@code SignatureEventService}'s guard.
     */
    @Column(name = "envelope_id", length = 64)
    private String envelopeId;

    /**
     * When the customer's {@code SIGNED} event landed — carried in callback 2's detail; null
     * unless signed. Added by UC 06.
     */
    @Column(name = "signed_at")
    private Instant signedAt;

    protected AgreementRecord() {
        // JPA
    }

    public AgreementRecord(String applicationId, AgreementStatus status) {
        this.applicationId = applicationId;
        this.status = status;
    }

    /**
     * Used where a case already has a registered envelope — today that is only test/fixture
     * setup standing in for the decision engine (UC 05), which is what will actually call this
     * once it lands.
     */
    public AgreementRecord(String applicationId, AgreementStatus status, String envelopeId) {
        this(applicationId, status);
        this.envelopeId = envelopeId;
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

    /**
     * UC 06's SIGNED transition: the status moves to {@link AgreementStatus#SIGNED} and
     * {@code signedAt} is stamped with the event's own {@code occurredAt} — not
     * {@code Instant.now()} — so the record carries when the customer actually signed, not when
     * this module happened to process it.
     */
    public void sign(Instant occurredAt) {
        this.status = AgreementStatus.SIGNED;
        this.signedAt = occurredAt;
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

    public String getEnvelopeId() {
        return envelopeId;
    }

    public Instant getSignedAt() {
        return signedAt;
    }
}
