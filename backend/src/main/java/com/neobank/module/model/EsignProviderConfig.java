package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * <h2>UC 07 · Operate Mock Control Panel — the mock's own dials.</h2>
 *
 * <p>Deliberately a SINGLETON row ({@link #SINGLETON_ID}), not a versioned table like {@code
 * AgreementConfig}: there is exactly one e-sign mock, and a dial change applies live to the next
 * envelope, never a history of past settings. See the UC 07 brief's entity model —
 * {@code EsignProviderConfig} "lives in the mock's schema, deliberately unversioned".</p>
 *
 * <p><b>Data effect note:</b> in the full ten-service system this table would live in the mock's
 * OWN schema, in its own service. It is stored here, in this module's schema, only because
 * standing up a second container is an infra change this branch is not allowed to make — see
 * {@link com.neobank.module.service.EsignMockService}'s class Javadoc. Nothing about the module's
 * own data (its {@code AgreementRecord}s) is read or written by this table.</p>
 */
@Entity
@Table(name = "esign_provider_config")
public class EsignProviderConfig {

    /** There is only ever one row. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EsignMode mode;

    @Column(name = "delay_seconds", nullable = false)
    private int delaySeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_outcome", nullable = false, length = 16)
    private EsignOutcome autoOutcome;

    /** Overrides expiry for NEW envelopes only; {@code null} means "use the real default". */
    @Column(name = "demo_expiry_seconds")
    private Integer demoExpirySeconds;

    protected EsignProviderConfig() {
        // JPA
    }

    public EsignProviderConfig(EsignMode mode, int delaySeconds, EsignOutcome autoOutcome,
                               Integer demoExpirySeconds) {
        this.id = SINGLETON_ID;
        this.mode = mode;
        this.delaySeconds = delaySeconds;
        this.autoOutcome = autoOutcome;
        this.demoExpirySeconds = demoExpirySeconds;
    }

    /**
     * Partial update: a {@code null} argument leaves that dial as it was. There is deliberately
     * no way to CLEAR {@code demoExpirySeconds} back to "use the real default" via this method —
     * not asked for by the brief, and a dedicated sentinel would be one more thing to explain on
     * an admin panel meant to be simple.
     */
    public void applyUpdate(EsignMode mode, Integer delaySeconds, EsignOutcome autoOutcome,
                            Integer demoExpirySeconds) {
        if (mode != null) {
            this.mode = mode;
        }
        if (delaySeconds != null) {
            this.delaySeconds = delaySeconds;
        }
        if (autoOutcome != null) {
            this.autoOutcome = autoOutcome;
        }
        if (demoExpirySeconds != null) {
            this.demoExpirySeconds = demoExpirySeconds;
        }
    }

    public EsignMode getMode() {
        return mode;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public EsignOutcome getAutoOutcome() {
        return autoOutcome;
    }

    public Integer getDemoExpirySeconds() {
        return demoExpirySeconds;
    }
}
