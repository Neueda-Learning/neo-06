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
 * <h2>Override Log — audit trail; one row per manual override, none ever deleted.</h2>
 *
 * <p>Written by UC 08 when an operator changes a case's workflow state with a reason.
 * Every override is audited: old status, new status, reason, operator, and timestamp
 * are all recorded and never modified.</p>
 *
 * <p>Per the spec: "SIGNED is never a legal target" for overrides — a signed contract
 * is a fact that cannot be undone by this mechanism.</p>
 */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The agreement that was overridden. */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** The status before the override. */
    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 32)
    private AgreementStatus oldStatus;

    /** The status after the override — SIGNED is never a legal target. */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private AgreementStatus newStatus;

    /** The mandatory justification typed by the operator. */
    @Column(nullable = false, length = 4000)
    private String reason;

    /** Who performed the override. */
    @Column(nullable = false, length = 128)
    private String operator;

    /** When it happened. */
    @Column(name = "overridden_at", nullable = false)
    private Instant overriddenAt;

    protected OverrideLog() {
        // JPA
    }

    public OverrideLog(String applicationId, AgreementStatus oldStatus, AgreementStatus newStatus,
                       String reason, String operator, Instant overriddenAt) {
        this.applicationId = applicationId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.operator = operator;
        this.overriddenAt = overriddenAt;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public AgreementStatus getOldStatus() {
        return oldStatus;
    }

    public AgreementStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getOverriddenAt() {
        return overriddenAt;
    }
}