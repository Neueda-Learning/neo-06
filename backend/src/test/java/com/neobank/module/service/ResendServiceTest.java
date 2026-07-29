package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC 04 · {@code POST /cases/{id}/resend} — see {@code docs/uc-04-pending-expired-queue.md}
 * AC3/AC4/AC5. No Spring, no database: every collaborator is mocked.
 */
class ResendServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private OrchestratorClient orchestrator;
    private EnvelopeService envelopes;
    private ResendService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        envelopes = mock(EnvelopeService.class);
        service = new ResendService(agreementRecords, history, orchestrator, envelopes);
        when(agreementRecords.save(any(AgreementRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(history.findByApplicationIdOrderByOccurredAtAsc(anyString())).thenReturn(List.of());
    }

    @Test
    void unknownApplicationIdIsReportedAsNoSuchElement() {
        when(agreementRecords.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend("ghost", "op1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void resendFromPendingRotatesTheEnvelopeResetsTheClockAndDoesNotCallTheOrchestrator() {
        AgreementRecord row = new AgreementRecord("app-1", AgreementStatus.PENDING, "env-old");
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(row));
        Instant sentAt = Instant.now();
        when(envelopes.register("app-1"))
                .thenReturn(new EnvelopeService.Registration("env-new", sentAt, sentAt.plusSeconds(5 * 24 * 3600)));

        service.resend("app-1", "operator-1");

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(row.getEnvelopeId()).isEqualTo("env-new");
        assertThat(row.getSentAt()).isEqualTo(sentAt);
        verify(agreementRecords).save(row);

        ArgumentCaptor<AgreementStatusHistory> saved = ArgumentCaptor.forClass(AgreementStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getEvent()).isEqualTo("RESENT");
        assertThat(saved.getValue().getFromStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(saved.getValue().getToStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(saved.getValue().getActor()).isEqualTo("operator-1");

        verify(orchestrator, never()).applicationStatusUpdate(any(), any(), any());
    }

    @Test
    void resendFromExpiredMovesBackToPendingAndReFiresCallbackOne() {
        AgreementRecord row = new AgreementRecord("app-2", AgreementStatus.EXPIRED, "env-old");
        when(agreementRecords.findById("app-2")).thenReturn(Optional.of(row));
        Instant sentAt = Instant.now();
        when(envelopes.register("app-2"))
                .thenReturn(new EnvelopeService.Registration("env-new2", sentAt, sentAt.plusSeconds(5 * 24 * 3600)));

        service.resend("app-2", "operator-2");

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(row.getEnvelopeId()).isEqualTo("env-new2");

        ArgumentCaptor<AgreementStatusHistory> saved = ArgumentCaptor.forClass(AgreementStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(AgreementStatus.EXPIRED);
        assertThat(saved.getValue().getToStatus()).isEqualTo(AgreementStatus.PENDING);

        verify(orchestrator).applicationStatusUpdate(eq("app-2"), eq(Decision.ACCEPTED), any());
    }

    @Test
    void resendOnASignedCaseIs409() {
        AgreementRecord row = new AgreementRecord("app-3", AgreementStatus.SIGNED, "env-old");
        when(agreementRecords.findById("app-3")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.resend("app-3", "operator-3"))
                .isInstanceOf(ResendNotAllowedException.class)
                .hasMessageContaining("app-3");

        verify(envelopes, never()).register(any());
        verify(agreementRecords, never()).save(any());
    }

    @Test
    void resendOnADeclinedCaseIs409() {
        AgreementRecord row = new AgreementRecord("app-4", AgreementStatus.DECLINED);
        when(agreementRecords.findById("app-4")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.resend("app-4", "operator-4"))
                .isInstanceOf(ResendNotAllowedException.class);
    }

    @Test
    void resendOnAGeneratingCaseIs409TooNoEnvelopeWasEverSent() {
        AgreementRecord row = new AgreementRecord("app-5", AgreementStatus.GENERATING);
        when(agreementRecords.findById("app-5")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.resend("app-5", "operator-5"))
                .isInstanceOf(ResendNotAllowedException.class);
    }
}
