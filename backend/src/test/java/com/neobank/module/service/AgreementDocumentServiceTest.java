package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /cases/{id}/document}'s read path (see {@code prompts/uc-05-prompt.md}): a stored
 * {@link OfferDocument} is served byte-for-byte, an unknown {@code applicationId} 404s, and a case
 * the consent gate declined before generation 409s. No PDF library involved — that is
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
    void unknownApplicationIdThrows() {
        when(offerDocuments.findByApplicationId("app-9999")).thenReturn(Optional.empty());
        when(agreementRecords.findById("app-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocument("app-9999"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("app-9999");
    }

    @Test
    void aConsentGateDeclineWithNoDocumentIsAConflictNotA404() {
        when(offerDocuments.findByApplicationId("app-5")).thenReturn(Optional.empty());
        when(agreementRecords.findById("app-5"))
                .thenReturn(Optional.of(new AgreementRecord("app-5", AgreementStatus.DECLINED)));

        assertThatThrownBy(() -> service.getDocument("app-5"))
                .isInstanceOf(CaseConflictException.class)
                .hasMessageContaining("app-5");
    }
}

