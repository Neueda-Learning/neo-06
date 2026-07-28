package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * <h2>The signature case — one row per {@code applicationId}, from {@code GENERATING} to a
 * terminal state.</h2>
 *
 * <p>Written by UC00 (this module's {@code /execute} — see
 * {@code module-06-agreement-management-docs/uc-00-process-application.md}): exactly one row per
 * {@code applicationId}, committed <em>before</em> the {@code 202} is sent. {@code applicationId}
 * is the {@code @Id} itself — the envelope's id, never the copy inside {@code application} — and
 * the ONLY applicant-related data this schema ever stores; the payload itself is never persisted,
 * only handed to the off-thread worker.</p>
 *
 * <p>UC00 only ever writes {@link AgreementStatus#GENERATING} and, on the consent gate,
 * {@link AgreementStatus#DECLINED} via {@link #changeStatus}. Everything past {@code status} —
 * {@code reference}, {@code envelopeId}, {@code termsVersion}, the limits, the timestamps — stays
 * {@code null} until a later use case (generation, sending, signature) fills it in; UC02 (Review
 * Agreement) is what reads the full shape back — see that brief's suggested entity model.</p>
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

    /** Human-facing case reference shown on screens and in both callbacks, e.g. {@code agr-000123}. */
    @Column(length = 32)
    private String reference;

    /** The CURRENT envelope registered with the e-sign mock. Null until sent. */
    @Column(name = "envelope_id", length = 64)
    private String envelopeId;

    /** The {@code AgreementConfig} version pinned at generation. Null until generated. */
    @Column(name = "terms_version", length = 32)
    private String termsVersion;

    /** Copied from {@code outputs.approvedLimit} at generation. */
    @Column(name = "approved_limit")
    private Integer approvedLimit;

    /** Copied from {@code outputs.apr} at generation. */
    @Column(precision = 5, scale = 2)
    private BigDecimal apr;

    /** Computed at generation: the greater of £5 or 3% of the limit, in whole pounds. */
    @Column(name = "min_payment_gbp")
    private Integer minPaymentGbp;

    /** When the envelope went out and callback 1 (PENDING) fired; null until sent. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** {@code sentAt} + {@code AgreementConfig.expiryDays} — null until sent. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** When the customer's SIGNED event landed; null unless signed. */
    @Column(name = "signed_at")
    private Instant signedAt;

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

    /** The shape UC00 writes: an id and its starting status, nothing else known yet. */
    public AgreementRecord(String applicationId, AgreementStatus status) {
        this.applicationId = applicationId;
        this.status = status;
    }

    /** The full shape a later use case (or a test fixture) populates once terms are pinned. */
    public AgreementRecord(String applicationId, AgreementStatus status, String reference,
                           String envelopeId, String termsVersion, Integer approvedLimit,
                           BigDecimal apr, Integer minPaymentGbp, Instant sentAt, Instant expiresAt,
                           Instant signedAt) {
        this.applicationId = applicationId;
        this.status = status;
        this.reference = reference;
        this.envelopeId = envelopeId;
        this.termsVersion = termsVersion;
        this.approvedLimit = approvedLimit;
        this.apr = apr;
        this.minPaymentGbp = minPaymentGbp;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.signedAt = signedAt;
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

    public String getReference() {
        return reference;
    }

    public String getEnvelopeId() {
        return envelopeId;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Integer getApprovedLimit() {
        return approvedLimit;
    }

    public BigDecimal getApr() {
        return apr;
    }

    public Integer getMinPaymentGbp() {
        return minPaymentGbp;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSignedAt() {
        return signedAt;
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
