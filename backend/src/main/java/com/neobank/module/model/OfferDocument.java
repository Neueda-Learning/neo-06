package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per application: the PDF this module generated once, at execute-time, plus the
 * fingerprint ({@link #sha256}, {@link #sizeBytes}) a caller can use to prove that what it
 * downloads today is exactly what was generated. See {@code prompts/uc-05-prompt.md} (UC05).
 *
 * <p><b>Write once, read many.</b> {@code AgreementDocumentComposer} is the only thing that ever
 * inserts a row here, and it does so at most once per {@code applicationId}. {@code GET
 * /cases/{id}/document} (backed by {@code AgreementDocumentService}) only ever selects; it never
 * regenerates the PDF or recomputes {@link #sha256} — that separation is what makes byte-identity
 * provable rather than just asserted.</p>
 */
@Entity
@Table(name = "offer_document")
public class OfferDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id the orchestrator gave us — one row per application, enforced by a unique index. */
    @Column(name = "application_id", nullable = false, unique = true, length = 64)
    private String applicationId;

    @Lob
    @Column(name = "pdf_blob", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] pdfBlob;

    /** Lowercase hex SHA-256 of {@link #pdfBlob} — computed once, at generation time, never again. */
    @Column(nullable = false, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected OfferDocument() {
        // JPA
    }

    public OfferDocument(String applicationId, byte[] pdfBlob, String sha256, long sizeBytes) {
        this.applicationId = applicationId;
        this.pdfBlob = pdfBlob;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void onCreate() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public byte[] getPdfBlob() {
        return pdfBlob;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
