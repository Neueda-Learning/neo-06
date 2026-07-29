package com.neobank.module.service;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
 * <h2>Still blocked on two upstream prerequisites — see the review note in {@link #compose}</h2>
 *
 * <p>{@link #renderAgreementPdf} — the layout, the field labels and the minimum-payment formula —
 * is fully implemented per the UC05 brief and unit-tested with fixed inputs (see
 * {@code AgreementDocumentComposerTest}). What it is <em>not</em> given yet, because neither exists
 * in this codebase, is the real {@code approvedLimit}/{@code apr}/{@code termsVersion} for a live
 * application: those render as {@link #PENDING} rather than a fabricated number until one of the
 * two prerequisites below lands. Deciding how to unblock that is left for review, not guessed at
 * here.</p>
 */
@Service
public class AgreementDocumentComposer {

    private static final Logger log = LoggerFactory.getLogger(AgreementDocumentComposer.class);

    /** Printed for a field whose real value does not exist yet — never a guessed number. */
    static final String PENDING = "pending";

    /**
     * The AgreementConfig Day-0 seed values named in the UC05 brief ({@code minPaymentPct} 3%,
     * {@code minPaymentFloorGbp} £5). Inlined here — see the class javadoc — only because there is
     * no {@code AgreementConfig} table yet for them to be read from.
     */
    private static final BigDecimal MIN_PAYMENT_PCT = new BigDecimal("0.03");

    private static final int MIN_PAYMENT_FLOOR_GBP = 5;

    private final OfferDocumentRepository offerDocuments;

    public AgreementDocumentComposer(OfferDocumentRepository offerDocuments) {
        this.offerDocuments = offerDocuments;
    }

    /**
     * Generates and stores the one {@code OfferDocument} row for {@code applicationId}, called
     * from {@link ApplicationService} at GENERATING time with the same {@link Application} the
     * envelope carried.
     *
     * <p>Idempotent: if a row already exists (a retried application, say), this does nothing —
     * {@link #sha256Hex} is computed exactly once per application and never overwritten.</p>
     *
     * <h2>⚠️ Blocked — flagged for review, not worked around</h2>
     *
     * <p>Per {@code prompts/uc-05-prompt.md} (AC2/AC3), the printed limit/APR/terms must come from
     * {@code outputs.approvedLimit}/{@code outputs.apr} (a v5 "Option A" envelope field) and the
     * current {@code AgreementConfig} version — never from finances, the product catalogue, or a
     * read-time recomputation. Neither exists in this repository today:</p>
     *
     * <ol>
     *   <li>{@link Application} carries no {@code outputs} block — and {@code integrations
     *       .orchestrator} is fixed by the platform contract (see its {@code package-info.java}),
     *       so this module cannot add one unilaterally.</li>
     *   <li>There is no {@code AgreementConfig} entity, table or Day-0 seed data — needed for the
     *       {@code termsVersion} footer and (properly) for the minimum-payment constants.</li>
     * </ol>
     *
     * <p>So only {@code applicant.fullName} and {@code product.productCode} — fields that
     * genuinely are in the envelope already — are passed to {@link #renderAgreementPdf} for a live
     * application; the rest render as {@link #PENDING}. Wiring the real values through is a
     * one-line change here once the two prerequisites above are resolved.</p>
     */
    public void compose(String applicationId, Application application) {
        if (offerDocuments.findByApplicationId(applicationId).isPresent()) {
            log.info("offer document already generated for {} — skipping", applicationId);
            return;
        }

        Application.Applicant applicant = application == null ? null : application.applicant();
        Application.Product product = application == null ? null : application.product();
        String fullName = applicant == null ? null : applicant.fullName();
        String productCode = product == null ? null : product.productCode();

        byte[] pdf = renderAgreementPdf(fullName, productCode, null, null, null);
        offerDocuments.save(new OfferDocument(applicationId, pdf, sha256Hex(pdf), pdf.length));
    }

    /**
     * Renders the one-page agreement: applicant, product, approved credit limit, APR, minimum
     * payment (computed here, see {@link #minPaymentGbp}) and the terms-version footer. Any field
     * whose real value is not supplied prints as {@link #PENDING} — never guessed.
     *
     * <p>Package-private so it can be unit-tested directly with fixed inputs (Maria's numbers,
     * AC2) independently of whatever this module can currently source live — see the blocked-on
     * note in {@link #compose}.</p>
     */
    byte[] renderAgreementPdf(String fullName, String productCode, Integer approvedLimit,
            BigDecimal apr, String termsVersion) {
        List<String> lines = new ArrayList<>();
        lines.add("Credit Agreement");
        lines.add("");
        lines.add("Applicant: " + orPending(fullName));
        lines.add("Product: " + orPending(productCode));
        lines.add("Approved credit limit: " + (approvedLimit == null
                ? PENDING
                : "£" + String.format("%,d", approvedLimit)));
        lines.add("APR: " + (apr == null ? PENDING : formatApr(apr) + "%"));
        lines.add("Minimum payment: " + (approvedLimit == null
                ? PENDING
                : "£" + minPaymentGbp(approvedLimit)));
        lines.add("");
        lines.add("Terms " + (termsVersion == null ? PENDING : "v" + termsVersion));

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(font, 12);
                float y = page.getMediaBox().getHeight() - 72;
                for (String line : lines) {
                    content.beginText();
                    content.newLineAtOffset(72, y);
                    content.showText(line);
                    content.endText();
                    y -= 18;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render agreement PDF", e);
        }
    }

    /**
     * {@code max(£5, 3% of approvedLimit)}, rounded to the nearest whole pound — Maria's
     * {@code £2,800} limit gives {@code £84}.
     */
    static int minPaymentGbp(int approvedLimit) {
        int threePercent = BigDecimal.valueOf(approvedLimit)
                .multiply(MIN_PAYMENT_PCT)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Math.max(MIN_PAYMENT_FLOOR_GBP, threePercent);
    }

    private static String formatApr(BigDecimal apr) {
        return apr.stripTrailingZeros().toPlainString();
    }

    private static String orPending(String value) {
        return value == null || value.isBlank() ? PENDING : value;
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
