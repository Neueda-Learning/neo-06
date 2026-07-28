package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.OfferDocumentRepository;
import java.io.IOException;
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
 * application, generated at most once, with a PDF that actually contains the placeholder text and
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
    void generatesAndStoresAOnePagePdfContainingThePlaceholderText() throws IOException {
        composer.compose("app-1234");

        ArgumentCaptor<OfferDocument> saved = ArgumentCaptor.forClass(OfferDocument.class);
        verify(offerDocuments).save(saved.capture());
        OfferDocument document = saved.getValue();

        assertThat(document.getApplicationId()).isEqualTo("app-1234");
        assertThat(document.getPdfBlob()).startsWith("%PDF".getBytes());
        assertThat(document.getSizeBytes()).isEqualTo(document.getPdfBlob().length);
        assertThat(document.getSha256()).isEqualTo(sha256Hex(document.getPdfBlob()));

        try (PDDocument pdf = Loader.loadPDF(document.getPdfBlob())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("hello world");
        }
    }

    @Test
    void isIdempotentWhenADocumentAlreadyExists() {
        when(offerDocuments.findByApplicationId("app-1234"))
                .thenReturn(Optional.of(new OfferDocument("app-1234", new byte[] {1}, "abc", 1)));

        composer.compose("app-1234");

        verify(offerDocuments, never()).save(any());
        verify(offerDocuments, times(1)).findByApplicationId("app-1234");
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
