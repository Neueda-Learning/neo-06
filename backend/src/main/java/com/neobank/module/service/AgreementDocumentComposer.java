package com.neobank.module.service;

import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generates the one-page agreement PDF and writes the single {@link OfferDocument} row for an
 * application — called once, at execute-time, from {@link ApplicationService}. See
 * {@code prompts/uc-05-prompt.md} (UC05, Serve the Agreement Document).
 *
 * <h2>Write path only</h2>
 *
 * <p>This is the only class in the module that references a PDF library. {@code GET
 * /cases/{id}/document} ({@link AgreementDocumentService}) only ever {@code SELECT}s the row this
 * class writes — keeping generation and serving apart is what makes byte-identity provable: there
 * is no code path on the read side that could produce different bytes.</p>
 *
 * <p>Renders the real terms pinned by {@code ApplicationService} via {@code AgreementConfig} — see
 * {@link Content} for exactly what is printed.</p>
 */
@Service
public class AgreementDocumentComposer {

    private static final Logger log = LoggerFactory.getLogger(AgreementDocumentComposer.class);

    private final OfferDocumentRepository offerDocuments;

    public AgreementDocumentComposer(OfferDocumentRepository offerDocuments) {
        this.offerDocuments = offerDocuments;
    }

    /** Everything the PDF's body prints — the terms {@code ApplicationService} pinned at generation. */
    public record Content(
            String signerName,
            String productCode,
            Integer approvedLimit,
            BigDecimal apr,
            Integer minPaymentGbp,
            String termsVersion) {
    }

    /**
     * Generates and stores the one {@code OfferDocument} row for {@code applicationId}, returning
     * its SHA-256 fingerprint — UC 07's e-sign mock registers an envelope against this value.
     *
     * <p>Idempotent: if a row already exists (a retried application, say), this does nothing but
     * return the ALREADY-stored fingerprint — {@link #sha256Hex} is computed exactly once per
     * application and never overwritten.</p>
     */
    public String compose(String applicationId, Content content) {
        var existing = offerDocuments.findByApplicationId(applicationId);
        if (existing.isPresent()) {
            log.info("offer document already generated for {} — skipping", applicationId);
            return existing.get().getSha256();
        }
        byte[] pdf = renderPdf(applicationId, content);
        String sha256 = sha256Hex(pdf);
        offerDocuments.save(new OfferDocument(applicationId, pdf, sha256, pdf.length));
        return sha256;
    }

    /** Renders a one-page PDF stating the case reference and the pinned terms. */
    private byte[] renderPdf(String applicationId, Content content) {
        List<String> lines = List.of(
                "Credit Agreement — " + applicationId,
                "Applicant: " + orDash(content.signerName()),
                "Product: " + orDash(content.productCode()),
                "Approved credit limit: £" + orDash(content.approvedLimit()),
                "APR: " + orDash(content.apr()) + "%",
                "Minimum monthly payment: £" + orDash(content.minPaymentGbp()),
                "Terms version: " + orDash(content.termsVersion()));
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float y = page.getMediaBox().getHeight() - 100;
                stream.beginText();
                stream.setFont(font, 16);
                stream.newLineAtOffset(72, y);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -24);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render agreement PDF", e);
        }
    }

    private static String orDash(Object value) {
        return value == null ? "—" : value.toString();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
