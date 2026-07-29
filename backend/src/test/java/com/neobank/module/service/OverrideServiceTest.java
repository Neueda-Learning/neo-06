package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.OverrideRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementConfig;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementConfigRepository;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import com.neobank.module.repository.OverrideLogRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UC08 — Override Case. No Spring: every collaborator is mocked, so this pins the doc's
 * acceptance criteria (AC1-AC6) without a running app.
 */
class OverrideServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementConfigRepository agreementConfigs;
    private AgreementStatusHistoryRepository history;
    private OverrideLogRepository overrideLogs;
    private OfferDocumentRepository offerDocuments;
    private ApplicantService applicants;
    private EsignProvider esignProvider;
    private OrchestratorClient orchestrator;
    private OverrideService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        agreementConfigs = mock(AgreementConfigRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        overrideLogs = mock(OverrideLogRepository.class);
        offerDocuments = mock(OfferDocumentRepository.class);
        applicants = mock(ApplicantService.class);
        esignProvider = mock(EsignProvider.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new OverrideService(agreementRecords, agreementConfigs, history, overrideLogs,
                offerDocuments, applicants, esignProvider, orchestrator);

        when(agreementConfigs.findTopByOrderByVersionDesc()).thenReturn(Optional.of(
                new AgreementConfig(1, "2026-06-01", 5, new BigDecimal("3.00"), 5,
                        new BigDecimal("24.9"))));
        when(offerDocuments.findByApplicationId(anyString())).thenReturn(Optional.empty());
        when(applicants.getApplicant(anyString()))
                .thenThrow(new OrchestratorUnavailableException("no sidecar in this test", null));
    }

    @Test
    void overridingASignedCaseIsA409WithTheExactMessage() {
        // AC3 checkpoint: SIGNED cases -> 409, error body says exactly
        // "no override may unsign a contract".
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.SIGNED);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.override("app-1",
                new OverrideRequest("DECLINED", "customer called in", "op-1")))
                .isInstanceOf(CaseConflictException.class)
                .hasMessage("no override may unsign a contract");
    }

    @Test
    void newStatusMustBePendingOrDeclined() {
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.override("app-1",
                new OverrideRequest("SIGNED", "reason", "op-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abandoningAnExpiredCaseRemovesItFromTheQueueByDecliningIt() {
        // AC6 checkpoint (first half): abandoning EXPIRED -> DECLINED removes it from the queue.
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.EXPIRED);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        service.override("app-1", new OverrideRequest("DECLINED", "abandoned by ops", "op-1"));

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.DECLINED);
        verify(agreementRecords).save(record);
        verify(overrideLogs).save(any());
        verify(history).save(any());
    }

    @Test
    void revivingADeclinedCaseRotatesTheEnvelopeAndResetsTheClock() {
        // AC6 checkpoint (second half): reviving DECLINED -> PENDING rotates envelope and resets
        // the clock.
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.DECLINED, "env-old");
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));
        when(esignProvider.registerEnvelope(eq("app-1"), any(), any()))
                .thenReturn(new EnvelopeRegistration("env-new", null));

        service.override("app-1", new OverrideRequest("PENDING", "customer asked again", "op-1"));

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(record.getEnvelopeId()).isEqualTo("env-new");
        assertThat(record.getExpiresAt()).isNotNull();
    }

    @Test
    void pendingToDeclinedStopsALiveOffer() {
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        service.override("app-1", new OverrideRequest("DECLINED", "customer withdrew", "op-1"));

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.DECLINED);
    }

    @Test
    void pendingToPendingIsNotALegalMove() {
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.override("app-1",
                new OverrideRequest("PENDING", "reason", "op-1")))
                .isInstanceOf(CaseConflictException.class);
    }

    @Test
    void writesBothAnOverrideLogRowAndAHistoryRow() {
        // AC4: "the timeline shows the human beside the machine" — both rows, every time.
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING);
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(record));

        service.override("app-1", new OverrideRequest("DECLINED", "reason", "op-1"));

        verify(overrideLogs).save(any());
        verify(history).save(any());
    }
}
