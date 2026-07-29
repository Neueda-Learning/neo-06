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
 * <h2>UC 08 · Override Case — the audit trail; one row per manual override, none ever deleted.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-08-override-case.md}'s suggested entity
 * model. Written alongside (never instead of) an {@link AgreementStatusHistory} row — "the
 * timeline shows the human beside the machine" (AC4).</p>
 */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 32)
    private AgreementStatus oldStatus;

    /** SIGNED is never a legal target — enforced by {@code OverrideService}, not this column. */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private AgreementStatus newStatus;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false, length = 64)
    private String operator;

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
