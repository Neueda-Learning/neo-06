package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * UC02 — Review Agreement. No Spring, no database: the repositories are mocked, so this pins
 * exactly what the doc's acceptance criteria require without needing a running app.
 */
class CaseServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private OfferDocumentRepository offerDocuments;
    private CaseService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        offerDocuments = mock(OfferDocumentRepository.class);
        when(offerDocuments.findByApplicationId(anyString())).thenReturn(Optional.empty());
        service = new CaseService(agreementRecords, history, offerDocuments);
    }

    @Test
    void unknownApplicationIdIsReportedAsNoSuchElementNeverA500() {
        when(agreementRecords.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCase("ghost"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void mariasCaseReturnsTheExactStoredTermsAndAnOldestFirstTimeline() {
        // The doc's checkpoint (AC2/AC3): app-1234, Maria Nowak's agreement.
        Instant sentAt = Instant.parse("2026-07-21T21:41:00Z");
        Instant signedAt = Instant.parse("2026-07-25T10:03:00Z");
        AgreementRecord maria = new AgreementRecord(
                "app-1234", AgreementStatus.SIGNED, "agr-000123", "env-8f14e45f", "2026-06-01",
                2800, new BigDecimal("24.9"), 84, sentAt, sentAt.plusSeconds(5 * 24 * 3600), signedAt);
        when(agreementRecords.findById("app-1234")).thenReturn(Optional.of(maria));
        when(offerDocuments.findByApplicationId("app-1234"))
                .thenReturn(Optional.of(new OfferDocument("app-1234", new byte[] {1}, "sha", 1)));

        AgreementStatusHistory sent = new AgreementStatusHistory("app-1234",
                AgreementStatus.GENERATING, AgreementStatus.PENDING, "ENVELOPE_SENT", "system", sentAt);
        AgreementStatusHistory signed = new AgreementStatusHistory("app-1234",
                AgreementStatus.PENDING, AgreementStatus.SIGNED, "SIGNATURE_EVENT", "customer", signedAt);
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-1234"))
                .thenReturn(List.of(sent, signed));

        var detail = service.getCase("app-1234");

        assertThat(detail.status()).isEqualTo("SIGNED");
        assertThat(detail.approvedLimit()).isEqualTo(2800);
        assertThat(detail.apr()).isEqualByComparingTo("24.9");
        assertThat(detail.minPaymentGbp()).isEqualTo(84);
        assertThat(detail.termsVersion()).isEqualTo("2026-06-01");
        assertThat(detail.signedAt()).isEqualTo(signedAt);

        assertThat(detail.timeline()).hasSize(2);
        assertThat(detail.timeline().get(0).toStatus()).isEqualTo("PENDING");
        assertThat(detail.timeline().get(0).event()).isEqualTo("ENVELOPE_SENT");
        assertThat(detail.timeline().get(1).toStatus()).isEqualTo("SIGNED");
        assertThat(detail.timeline().get(1).event()).isEqualTo("SIGNATURE_EVENT");
        // The last row is the terminal transition and matches signedAt (AC4).
        assertThat(detail.timeline().get(1).occurredAt()).isEqualTo(detail.signedAt());
        assertThat(detail.documentAvailable()).isTrue();
    }

    @Test
    void aConsentGateDeclineHasNoEnvelopeOrSentAt() {
        // AC5: a single GENERATING -> DECLINED (CONSENT_GATE) row, no envelopeId, no sentAt.
        AgreementRecord declined = new AgreementRecord("app-5", AgreementStatus.DECLINED);
        when(agreementRecords.findById("app-5")).thenReturn(Optional.of(declined));

        Instant now = Instant.parse("2026-07-21T21:41:00Z");
        AgreementStatusHistory consentGate = new AgreementStatusHistory("app-5",
                AgreementStatus.GENERATING, AgreementStatus.DECLINED, "CONSENT_GATE", "system", now);
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-5")).thenReturn(List.of(consentGate));

        var detail = service.getCase("app-5");

        assertThat(detail.status()).isEqualTo("DECLINED");
        assertThat(detail.envelopeId()).isNull();
        assertThat(detail.sentAt()).isNull();
        assertThat(detail.timeline()).singleElement()
                .satisfies(entry -> assertThat(entry.event()).isEqualTo("CONSENT_GATE"));
        // A consent-gate decline never generated a document — the UI must not offer to view one.
        assertThat(detail.documentAvailable()).isFalse();
    }

    @Test
    void termsAreReadFromTheStoredRowNeverRecomputed() {
        // AC6: nothing here should depend on "the current config version" — there is no such
        // collaborator injected into CaseService at all, so a stored row's terms cannot drift.
        AgreementRecord record = new AgreementRecord(
                "app-9", AgreementStatus.SIGNED, "agr-1", "env-1", "2026-06-01",
                1000, new BigDecimal("19.9"), 30, Instant.now(), Instant.now(), Instant.now());
        when(agreementRecords.findById("app-9")).thenReturn(Optional.of(record));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-9")).thenReturn(List.of());

        assertThat(service.getCase("app-9").termsVersion()).isEqualTo("2026-06-01");
    }
}
