package com.neobank.module.service;

import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
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

    private static final Color BRAND_COLOR = new Color(11, 45, 74);
    private static final Color RULE_COLOR = new Color(200, 203, 208);
    private static final Color MUTED_TEXT = new Color(95, 100, 110);
    private static final Color BODY_TEXT = new Color(30, 32, 36);
    private static final float MARGIN = 56f;

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

    /**
     * Renders a one-page PDF styled like a real bank credit agreement: a letterhead band, the
     * applicant's details, the priced terms, the standard boilerplate, and a signature block —
     * rather than a plain list of lines.
     */
    private byte[] renderPdf(String applicationId, Content content) {
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float contentRight = pageWidth - MARGIN;

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                // Letterhead band.
                float bandHeight = 78f;
                stream.setNonStrokingColor(BRAND_COLOR);
                stream.addRect(0, pageHeight - bandHeight, pageWidth, bandHeight);
                stream.fill();
                drawText(stream, bold, 20, MARGIN, pageHeight - 34, "NEO BANK", Color.WHITE);
                drawText(stream, regular, 12, MARGIN, pageHeight - 54, "Credit Agreement", Color.WHITE);

                float y = pageHeight - bandHeight - 30;
                drawText(stream, regular, 9, MARGIN, y, "Application reference: " + applicationId,
                        MUTED_TEXT);
                y -= 24;

                y = drawSection(stream, bold, regular, MARGIN, contentRight, y, "Applicant",
                        List.<String[]>of(new String[] {"Name", orDash(content.signerName())}));
                y -= 10;

                y = drawSection(stream, bold, regular, MARGIN, contentRight, y, "Agreement terms",
                        List.<String[]>of(
                                new String[] {"Product", orDash(content.productCode())},
                                new String[] {"Approved credit limit", "£" + orDash(content.approvedLimit())},
                                new String[] {"APR", orDash(content.apr()) + "%"},
                                new String[] {"Minimum monthly payment", "£" + orDash(content.minPaymentGbp())},
                                new String[] {"Terms version", orDash(content.termsVersion())}));
                y -= 16;

                String boilerplate = "This Credit Agreement is made between NEO Bank plc "
                        + "(\"the Bank\") and the Applicant named above for the product and the "
                        + "credit limit stated above. By signing below, or by accepting "
                        + "electronically through the Bank's e-signature service, the Applicant "
                        + "agrees to repay all sums drawn under this agreement together with "
                        + "interest at the APR stated above, and to pay at least the minimum "
                        + "monthly payment stated above by each due date, in accordance with the "
                        + "terms and conditions identified by the Terms version above.";
                float bodyWidth = contentRight - MARGIN;
                for (String line : wrap(boilerplate, regular, 10, bodyWidth)) {
                    drawText(stream, regular, 10, MARGIN, y, line, BODY_TEXT);
                    y -= 14;
                }

                float signatureY = 150f;
                drawRule(stream, MARGIN, MARGIN + 220, signatureY, RULE_COLOR);
                drawText(stream, regular, 9, MARGIN, signatureY - 12, "Applicant signature", MUTED_TEXT);
                drawRule(stream, contentRight - 140, contentRight, signatureY, RULE_COLOR);
                drawText(stream, regular, 9, contentRight - 140, signatureY - 12, "Date", MUTED_TEXT);

                float footerY = 60f;
                drawRule(stream, MARGIN, contentRight, footerY + 18, RULE_COLOR);
                drawText(stream, italic, 8, MARGIN, footerY,
                        "System-generated agreement — " + applicationId + " — page 1 of 1", MUTED_TEXT);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render agreement PDF", e);
        }
    }

    /** A bold section title, a thin rule beneath it, then one label/value row per pair. */
    private static float drawSection(PDPageContentStream stream, PDFont bold, PDFont regular,
            float left, float right, float y, String title, List<String[]> rows) throws IOException {
        drawText(stream, bold, 12, left, y, title, BRAND_COLOR);
        y -= 6;
        drawRule(stream, left, right, y, RULE_COLOR);
        y -= 18;
        for (String[] row : rows) {
            drawText(stream, bold, 10, left, y, row[0] + ":", MUTED_TEXT);
            drawText(stream, regular, 10, left + 160, y, row[1], BODY_TEXT);
            y -= 18;
        }
        return y;
    }

    private static void drawText(PDPageContentStream stream, PDFont font, float size, float x,
            float y, String text, Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private static void drawRule(PDPageContentStream stream, float x1, float x2, float y,
            Color color) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(0.75f);
        stream.moveTo(x1, y);
        stream.lineTo(x2, y);
        stream.stroke();
    }

    /** Greedy word-wrap against the font's own metrics — PDFBox never does this for you. */
    private static List<String> wrap(String text, PDFont font, float size, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * size;
            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
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
