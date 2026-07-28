package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * <h2>The signature case — one row per {@code applicationId}, from {@code GENERATING} to a
 * terminal state.</h2>
 *
 * <p>Written by UC00 (this module's {@code /execute} — see
 * {@code module-06-agreement-management-docs/uc-00-process-application.md}): exactly one row per
 * {@code applicationId}, committed <em>before</em> the {@code 202} is sent. Everything past
 * {@code applicationId} and {@code status} is {@code null} until a later use case fills it in
 * (generation, sending, signature) — the lifecycle transitions themselves are out of scope for
 * both UC00 and UC02 ("the /execute wiring", "editing an agreement... is UC 08").</p>
 *
 * <p><b>Idempotency = the unique key on {@code applicationId}.</b> It is the {@code @Id} directly
 * (not a surrogate long) — the journey key from the envelope, and the only applicant-related
 * column in this schema (see the doc's platform rule: "the payload is NEVER stored — only
 * {@code applicationId}").</p>
 */
@Entity
@Table(name = "agreement_record")
public class AgreementRecord {

    /** The journey key from the envelope — the only applicant-related column in this schema. */
    @Id
    @Column(name = "application_id", nullable = false, length = 64)
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

    /** When this row was created — used only to order this module's own board, newest first. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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
}
