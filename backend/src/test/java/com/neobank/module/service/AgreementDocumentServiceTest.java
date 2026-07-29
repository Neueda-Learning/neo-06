package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /cases/{id}/document}'s read path (see {@code prompts/uc-05-prompt.md}): a stored
 * {@link OfferDocument} is served byte-for-byte regardless of the case's current status (AC6); an
 * unknown {@code applicationId} 404s; a real case whose consent gate declined it before any
 * document was generated 409s (AC7). No PDF library involved — that is
 * {@link AgreementDocumentComposer}'s job, exercised in {@link AgreementDocumentComposerTest}.
 */
class AgreementDocumentServiceTest {

    private final OfferDocumentRepository offerDocuments = mock(OfferDocumentRepository.class);
    private final AgreementRecordRepository agreementRecords = mock(AgreementRecordRepository.class);
    private final AgreementDocumentService service =
            new AgreementDocumentService(offerDocuments, agreementRecords);

    @Test
    void servesTheStoredPdfByteForByte() {
        byte[] pdf = {1, 2, 3};
        when(offerDocuments.findByApplicationId("app-1234"))
                .thenReturn(Optional.of(new OfferDocument("app-1234", pdf, "deadbeef", pdf.length)));

        var document = service.getDocument("app-1234");

        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.fileName()).isEqualTo("agreement-app-1234.pdf");
        assertThat(document.content()).isEqualTo(pdf);
    }

    @Test
    void unknownApplicationIdThrowsNotFound() {
        when(offerDocuments.findByApplicationId("app-9999")).thenReturn(Optional.empty());
        when(agreementRecords.existsById("app-9999")).thenReturn(false);

        assertThatThrownBy(() -> service.getDocument("app-9999"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("app-9999");
    }

    @Test
    void aConsentGateCaseWithNoDocumentThrowsNotGeneratedConflict() {
        when(offerDocuments.findByApplicationId("app-declined")).thenReturn(Optional.empty());
        when(agreementRecords.existsById("app-declined")).thenReturn(true);

        assertThatThrownBy(() -> service.getDocument("app-declined"))
                .isInstanceOf(AgreementDocumentNotGeneratedException.class)
                .hasMessageContaining("app-declined");
    }
}

