package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <h2>Insert-only, versioned terms — the current version is the highest. Added while wiring
 * UC 00/02/05 together end to end.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-00-process-application.md}'s suggested
 * entity model. Rows are inserted, never updated (a config change is a new row); a case pins the
 * version in force at GENERATING time and never re-points, even after a newer version is seeded
 * (see UC 02 AC6).</p>
 *
 * <p><b>One field beyond the brief's own table: {@link #aprPercent}.</b> The brief's field table
 * says {@code approvedLimit}/{@code apr} arrive on the envelope's {@code outputs} block ("v5
 * Option A", priced by a module 5 decision engine). The fixed platform contract this repo
 * actually implements (see {@code AGENTS.md} — {@code neo-00/api-contract.md}, not the v5 draft)
 * has no such block: {@link com.neobank.module.integrations.orchestrator.Application} carries
 * only {@code product.requestedCreditLimit}, the customer's ASK, never an approved figure or a
 * priced rate. Rather than leave every case parked with "outputs missing" forever — which would
 * make the whole lifecycle undemoable — this module treats {@code requestedCreditLimit} as the
 * approved limit and prices every agreement at this config's {@link #aprPercent}, exactly the way
 * {@code minPaymentPct}/{@code minPaymentFloorGbp} already price the minimum payment. A real
 * module 5 integration would replace both reads with the real {@code outputs} block; nothing
 * else about this table changes shape.</p>
 */
@Entity
@Table(name = "agreement_config")
public class AgreementConfig {

    /** One new row per change; current = {@code MAX(version)}. */
    @Id
    private Integer version;

    /** The legal terms identifier stamped on every PDF generated under this version. */
    @Column(name = "terms_version", nullable = false, length = 32)
    private String termsVersion;

    /** How long a case may stay PENDING before the clock expires it. */
    @Column(name = "expiry_days", nullable = false)
    private int expiryDays;

    /** The percentage leg of the minimum-payment rule, e.g. {@code 3.00} for 3%. */
    @Column(name = "min_payment_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPaymentPct;

    /** The floor leg — the minimum payment is never below this. */
    @Column(name = "min_payment_floor_gbp", nullable = false)
    private int minPaymentFloorGbp;

    /** See the class javadoc: stands in for module 5's per-application priced APR. */
    @Column(name = "apr_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal aprPercent;

    protected AgreementConfig() {
        // JPA
    }

    public AgreementConfig(Integer version, String termsVersion, int expiryDays,
                           BigDecimal minPaymentPct, int minPaymentFloorGbp, BigDecimal aprPercent) {
        this.version = version;
        this.termsVersion = termsVersion;
        this.expiryDays = expiryDays;
        this.minPaymentPct = minPaymentPct;
        this.minPaymentFloorGbp = minPaymentFloorGbp;
        this.aprPercent = aprPercent;
    }

    /** The greater of the floor or {@code minPaymentPct}% of the limit, in whole pounds. */
    public int minPaymentFor(int approvedLimit) {
        BigDecimal pctAmount = minPaymentPct
                .multiply(BigDecimal.valueOf(approvedLimit))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
        return Math.max(minPaymentFloorGbp, pctAmount.intValue());
    }

    public Integer getVersion() {
        return version;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public int getExpiryDays() {
        return expiryDays;
    }

    public BigDecimal getMinPaymentPct() {
        return minPaymentPct;
    }

    public int getMinPaymentFloorGbp() {
        return minPaymentFloorGbp;
    }

    public BigDecimal getAprPercent() {
        return aprPercent;
    }
}
