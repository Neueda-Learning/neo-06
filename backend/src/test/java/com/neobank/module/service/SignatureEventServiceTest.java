package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.dto.SignatureEventResponse;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC 06's state table, one branch per test — "the richest test surface in the module" per the
 * brief. No Spring, no database, no HTTP: {@link SignatureEventService} takes a repository, a
 * history repository and the orchestrator client, all mocked here.
 */
class SignatureEventServiceTest {

    private static final String ENVELOPE = "env-8f14e45f";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:03:00Z");

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private OrchestratorClient orchestrator;
    private SignatureEventService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new SignatureEventService(agreementRecords, history, orchestrator);
    }

    private static SignatureEventRequest request(SignatureEventType event) {
        return new SignatureEventRequest(ENVELOPE, event, OCCURRED_AT);
    }

    private static AgreementRecord pendingCase(String id) {
        return new AgreementRecord(id, AgreementStatus.PENDING, ENVELOPE);
    }

    @Test
    void signedOnAPendingCaseAdvancesItAndReportsAccepted() {
        AgreementRecord record = pendingCase("APP-1");
        when(agreementRecords.findById("APP-1")).thenReturn(Optional.of(record));

        SignatureEventResponse response = service.apply("APP-1", request(SignatureEventType.SIGNED));

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.SIGNED);
        assertThat(record.getSignedAt()).isEqualTo(OCCURRED_AT);
        assertThat(response.status()).isEqualTo("SIGNED");
        assertThat(response.replay()).isFalse();

        ArgumentCaptor<AgreementStatusHistory> saved = ArgumentCaptor.forClass(AgreementStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(saved.getValue().getToStatus()).isEqualTo(AgreementStatus.SIGNED);
        assertThat(saved.getValue().getEvent()).isEqualTo("SIGNATURE_EVENT");

        verify(orchestrator).applicationStatusUpdate(eq("APP-1"), eq(Decision.ACCEPTED), any());
    }

    @Test
    void declinedOnAPendingCaseAdvancesItAndReportsRejected() {
        AgreementRecord record = pendingCase("APP-2");
        when(agreementRecords.findById("APP-2")).thenReturn(Optional.of(record));

        SignatureEventResponse response = service.apply("APP-2", request(SignatureEventType.DECLINED));

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.DECLINED);
        assertThat(record.getSignedAt()).isNull();
        assertThat(response.status()).isEqualTo("DECLINED");

        verify(history).save(any(AgreementStatusHistory.class));
        verify(orchestrator).applicationStatusUpdate(eq("APP-2"), eq(Decision.REJECTED), any());
    }

    @Test
    void theIdenticalEventPostedAgainIsAnIdempotentReplayWithNoNewHistoryOrCallback() {
        // Already SIGNED, on the same envelope: a replay of the same event, not a new decision.
        AgreementRecord record = new AgreementRecord("APP-3", AgreementStatus.SIGNED, ENVELOPE);
        when(agreementRecords.findById("APP-3")).thenReturn(Optional.of(record));

        SignatureEventResponse response = service.apply("APP-3", request(SignatureEventType.SIGNED));

        assertThat(response.replay()).isTrue();
        assertThat(response.status()).isEqualTo("SIGNED");
        verifyNoInteractions(history);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aSignedEventAfterTheClockExpiredTheCaseIsRefusedButAudited() {
        AgreementRecord record = new AgreementRecord("APP-4", AgreementStatus.EXPIRED, ENVELOPE);
        when(agreementRecords.findById("APP-4")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.apply("APP-4", request(SignatureEventType.SIGNED)))
                .isInstanceOf(SignatureEventConflictException.class);

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.EXPIRED); // stays EXPIRED
        ArgumentCaptor<AgreementStatusHistory> saved = ArgumentCaptor.forClass(AgreementStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getEvent()).isEqualTo("REFUSED_LATE_EVENT");
        assertThat(saved.getValue().getFromStatus()).isEqualTo(AgreementStatus.EXPIRED);
        assertThat(saved.getValue().getToStatus()).isEqualTo(AgreementStatus.EXPIRED);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void aStaleEnvelopeRotatedAwayByAResendIsRefused() {
        AgreementRecord record = new AgreementRecord("APP-5", AgreementStatus.PENDING, "env-new");
        when(agreementRecords.findById("APP-5")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.apply("APP-5", request(SignatureEventType.SIGNED)))
                .isInstanceOf(SignatureEventConflictException.class)
                .hasMessageContaining("envelope");

        verifyNoInteractions(history);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void anEventOnAGeneratingCaseIsRefused() {
        // GENERATING never had an envelope registered, so envelopeId is null and cannot match.
        AgreementRecord record = new AgreementRecord("APP-6", AgreementStatus.GENERATING);
        when(agreementRecords.findById("APP-6")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.apply("APP-6", request(SignatureEventType.SIGNED)))
                .isInstanceOf(SignatureEventConflictException.class);

        verifyNoInteractions(orchestrator);
    }

    @Test
    void aContradictingEventOnAnAlreadyDeclinedCaseIsRefused() {
        AgreementRecord record = new AgreementRecord("APP-7", AgreementStatus.DECLINED, ENVELOPE);
        when(agreementRecords.findById("APP-7")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.apply("APP-7", request(SignatureEventType.SIGNED)))
                .isInstanceOf(SignatureEventConflictException.class);

        verify(history, never()).save(any());
        verifyNoInteractions(orchestrator);
    }

    @Test
    void anUnknownApplicationIdIs404() {
        when(agreementRecords.findById("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply("GHOST", request(SignatureEventType.SIGNED)))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(history);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void exactlyOneCallbackFiresPerTerminalTransitionEvenAcrossRepeatedCalls() {
        AgreementRecord record = pendingCase("APP-8");
        when(agreementRecords.findById("APP-8")).thenReturn(Optional.of(record));

        service.apply("APP-8", request(SignatureEventType.SIGNED));
        service.apply("APP-8", request(SignatureEventType.SIGNED)); // replay

        verify(orchestrator, times(1)).applicationStatusUpdate(eq("APP-8"), eq(Decision.ACCEPTED), any());
        verify(history, times(1)).save(any());
    }
}
