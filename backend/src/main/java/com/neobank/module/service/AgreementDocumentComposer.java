package com.neobank.module.service;

import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * <p>Every real precondition is still skipped: there is no {@code AgreementConfig}, no
 * {@code outputs.approvedLimit}/{@code apr}, no consent gate. The PDF's body is the fixed
 * {@link #PLACEHOLDER_TEXT}, standing in for the real per-application terms the finished use case
 * computes and prints.</p>
 */
@Service
public class AgreementDocumentComposer {

    private static final Logger log = LoggerFactory.getLogger(AgreementDocumentComposer.class);

    /** Stand-in for the real per-application terms — no AgreementConfig, no outputs, yet. */
    private static final String PLACEHOLDER_TEXT = "hello world";

    private final OfferDocumentRepository offerDocuments;

    public AgreementDocumentComposer(OfferDocumentRepository offerDocuments) {
        this.offerDocuments = offerDocuments;
    }

    /**
     * Generates and stores the one {@code OfferDocument} row for {@code applicationId}.
     *
     * <p>Idempotent: if a row already exists (a retried application, say), this does nothing —
     * {@link #sha256Hex} is computed exactly once per application and never overwritten.</p>
     */
    public void compose(String applicationId) {
        if (offerDocuments.findByApplicationId(applicationId).isPresent()) {
            log.info("offer document already generated for {} — skipping", applicationId);
            return;
        }
        byte[] pdf = renderPlaceholderPdf();
        offerDocuments.save(new OfferDocument(applicationId, pdf, sha256Hex(pdf), pdf.length));
    }

    /** Renders a one-page PDF whose whole body is {@link #PLACEHOLDER_TEXT}. */
    private byte[] renderPlaceholderPdf() {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                content.newLineAtOffset(72, page.getMediaBox().getHeight() - 120);
                content.showText(PLACEHOLDER_TEXT);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render agreement PDF", e);
        }
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
