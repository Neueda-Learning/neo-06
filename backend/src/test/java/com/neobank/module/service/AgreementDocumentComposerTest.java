package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The write path of UC05 (see {@code prompts/uc-05-prompt.md}): one {@link OfferDocument} row per
 * application, generated at most once, with a PDF that actually contains the required content and
 * a SHA-256 that actually matches the stored bytes.
 */
class AgreementDocumentComposerTest {

    private OfferDocumentRepository offerDocuments;
    private AgreementDocumentComposer composer;

    @BeforeEach
    void setUp() {
        offerDocuments = mock(OfferDocumentRepository.class);
        composer = new AgreementDocumentComposer(offerDocuments);
        when(offerDocuments.findByApplicationId(any())).thenReturn(Optional.empty());
    }

    @Test
    void generatesAndStoresAOnePagePdfWithTheApplicantAndProductFromTheEnvelope() throws IOException {
        composer.compose("app-1234", applicationOf("Maria Nowak", "CREDIT_CARD_REWARDS"));

        ArgumentCaptor<OfferDocument> saved = ArgumentCaptor.forClass(OfferDocument.class);
        verify(offerDocuments).save(saved.capture());
        OfferDocument document = saved.getValue();

        assertThat(document.getApplicationId()).isEqualTo("app-1234");
        assertThat(document.getPdfBlob()).startsWith("%PDF".getBytes());
        assertThat(document.getSizeBytes()).isEqualTo(document.getPdfBlob().length);
        assertThat(document.getSha256()).isEqualTo(sha256Hex(document.getPdfBlob()));

        try (PDDocument pdf = Loader.loadPDF(document.getPdfBlob())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            // Genuinely available from the envelope today — see the class javadoc for why the
            // limit/APR/terms below are not.
            assertThat(text).contains("Maria Nowak").contains("CREDIT_CARD_REWARDS");
            assertThat(text).contains("pending");
        }
    }

    @Test
    void isIdempotentWhenADocumentAlreadyExists() {
        when(offerDocuments.findByApplicationId("app-1234"))
                .thenReturn(Optional.of(new OfferDocument("app-1234", new byte[] {1}, "abc", 1)));

        composer.compose("app-1234", applicationOf("Maria Nowak", "CREDIT_CARD_REWARDS"));

        verify(offerDocuments, never()).save(any());
        verify(offerDocuments, times(1)).findByApplicationId("app-1234");
    }

    /**
     * AC2's checkpoint — Maria's document, given fixed inputs (the numbers a real
     * {@code outputs}/{@code AgreementConfig} would supply once they exist; see the blocked-on
     * note in {@link AgreementDocumentComposer#compose}): exact limit, APR, computed minimum
     * payment and footer.
     */
    @Test
    void renderAgreementPdfProducesMariasExactCheckpointText() throws IOException {
        byte[] pdf = composer.renderAgreementPdf(
                "Maria Nowak", "CREDIT_CARD_REWARDS", 2800, new BigDecimal("24.90"), "2026-06-01");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Maria Nowak");
            assertThat(text).contains("CREDIT_CARD_REWARDS");
            assertThat(text).contains("£2,800");
            assertThat(text).contains("24.9%");
            assertThat(text).contains("£84");
            assertThat(text).contains("Terms v2026-06-01");
        }
    }

    @Test
    void minPaymentGbpIsTheGreaterOfTheFloorAndThreePercent() {
        assertThat(AgreementDocumentComposer.minPaymentGbp(2800)).isEqualTo(84);
        assertThat(AgreementDocumentComposer.minPaymentGbp(100)).isEqualTo(5);
        assertThat(AgreementDocumentComposer.minPaymentGbp(10_000)).isEqualTo(300);
    }

    private static Application applicationOf(String fullName, String productCode) {
        return new Application(
                "app-1234", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant(fullName, "1996-04-11", "maria@example.com",
                        "+48000000000", "PL", "PL", java.util.List.of("PL"), "RENTING",
                        new Application.Address("1 Main St", null, "Warsaw", "00-001", "PL"),
                        24, 0),
                null, null, null,
                new Application.Product(productCode, 3000),
                null, null);
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
            throw new IllegalStateException(e);
        }
    }
}

