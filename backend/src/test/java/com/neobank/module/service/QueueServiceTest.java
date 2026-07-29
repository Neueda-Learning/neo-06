package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementConfig;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementConfigRepository;
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

/**
 * UC04 — Pending &amp; Expired Queue. No Spring: every collaborator is mocked, so this pins the
 * doc's acceptance criteria (AC1-AC5) without a running app.
 */
class QueueServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementConfigRepository agreementConfigs;
    private AgreementStatusHistoryRepository history;
    private OfferDocumentRepository offerDocuments;
    private ApplicantService applicants;
    private EsignProvider esignProvider;
    private OrchestratorClient orchestrator;
    private QueueService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        agreementConfigs = mock(AgreementConfigRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        offerDocuments = mock(OfferDocumentRepository.class);
        applicants = mock(ApplicantService.class);
        esignProvider = mock(EsignProvider.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new QueueService(agreementRecords, agreementConfigs, history, offerDocuments,
                applicants, esignProvider, orchestrator);

        when(agreementConfigs.findTopByOrderByVersionDesc()).thenReturn(Optional.of(
                new AgreementConfig(1, "2026-06-01", 5, new BigDecimal("3.00"), 5,
                        new BigDecimal("24.9"))));
        when(offerDocuments.findByApplicationId(anyString())).thenReturn(Optional.empty());
        when(applicants.getApplicant(anyString()))
                .thenThrow(new OrchestratorUnavailableException("no sidecar in this test", null));
    }

    @Test
    void listReturnsOnlyThatStateOldestFirst() {
        AgreementRecord pending = new AgreementRecord("app-1", AgreementStatus.PENDING);
        when(agreementRecords.findByStatusOrderBySentAtAsc(AgreementStatus.PENDING))
                .thenReturn(List.of(pending));
        when(history.countByApplicationIdAndToStatus("app-1", AgreementStatus.PENDING))
                .thenReturn(2L);

        var entries = service.list(AgreementStatus.PENDING, 10);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).applicationId()).isEqualTo("app-1");
        assertThat(entries.get(0).envelopeCount()).isEqualTo(2L);
    }

    @Test
    void resendFromPendingRotatesTheEnvelopeAndStaysPending() {
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING,
                "env-old");
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));
        when(esignProvider.registerEnvelope(eq("app-1"), any(), any()))
                .thenReturn(new EnvelopeRegistration("env-new", null));

        service.resend("app-1", "operator-1");

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(record.getEnvelopeId()).isEqualTo("env-new");
        verify(agreementRecords).save(record);
        verify(history).save(org.mockito.ArgumentMatchers.argThat(h -> h.getEvent().equals("RESENT")));
        verify(orchestrator, never()).applicationStatusUpdate(anyString(), any(), anyString());
    }

    @Test
    void resendFromExpiredRevivesToPendingAndFiresCallbackOne() {
        // AC4 checkpoint: resend from EXPIRED -> 200, status returns to PENDING, fresh
        // clock+envelope, history shows EXPIRED->PENDING (RESEND), callback 1 fires again.
        AgreementRecord record = new AgreementRecord("app-2", AgreementStatus.EXPIRED, "env-old");
        when(agreementRecords.findById("app-2")).thenReturn(Optional.of(record));
        when(esignProvider.registerEnvelope(eq("app-2"), any(), any()))
                .thenReturn(new EnvelopeRegistration("env-new", null));

        service.resend("app-2", "operator-1");

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(record.getEnvelopeId()).isEqualTo("env-new");
        verify(history).save(org.mockito.ArgumentMatchers.argThat(h ->
                h.getEvent().equals("RESEND") && h.getFromStatus() == AgreementStatus.EXPIRED
                        && h.getToStatus() == AgreementStatus.PENDING));
        verify(orchestrator).applicationStatusUpdate(eq("app-2"), eq(Decision.ACCEPTED), anyString());
    }

    @Test
    void resendFromSignedIsA409() {
        AgreementRecord record = new AgreementRecord("app-3", AgreementStatus.SIGNED);
        when(agreementRecords.findById("app-3")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.resend("app-3", "operator-1"))
                .isInstanceOf(CaseConflictException.class);
    }

    @Test
    void resendFromDeclinedIsA409() {
        AgreementRecord record = new AgreementRecord("app-4", AgreementStatus.DECLINED);
        when(agreementRecords.findById("app-4")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.resend("app-4", "operator-1"))
                .isInstanceOf(CaseConflictException.class);
    }

    @Test
    void resendOnUnknownCaseIsA404() {
        when(agreementRecords.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend("ghost", "operator-1"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
