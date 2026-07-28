package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * UC00 — the hand-off between the request thread (insert the row, idempotently) and the worker
 * (decide and report). No Spring, no database, no HTTP — the service takes a request and calls
 * two collaborators, so the test is a handful of lines.
 */
class ApplicationServiceTest {

    private AgreementRecordRepository agreementRecords;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        // Runnable::run — the work happens inline, so there is nothing to wait for.
        service = new ApplicationService(Runnable::run, agreementRecords, orchestrator);
        when(agreementRecords.save(any(AgreementRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ApplicationRequest request(String id) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", null, null, null, null,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void aFirstExecuteInsertsTheRowBeforeDispatchingTheAsyncWorker() {
        when(agreementRecords.existsById("SIM-01")).thenReturn(false);
        when(agreementRecords.findById("SIM-01"))
                .thenReturn(Optional.of(new AgreementRecord("SIM-01", AgreementStatus.GENERATING)));

        service.processApplicationAsync(request("SIM-01"));

        ArgumentCaptor<AgreementRecord> saved = ArgumentCaptor.forClass(AgreementRecord.class);
        InOrder order = inOrder(agreementRecords, orchestrator);
        order.verify(agreementRecords).existsById("SIM-01");
        order.verify(agreementRecords).save(saved.capture());
        order.verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "hello world from processApplication");

        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getStatus()).isEqualTo(AgreementStatus.GENERATING);
    }

    @Test
    void aDuplicateExecuteForAKnownIdSkipsTheInsertAndTheAsyncWorker() {
        when(agreementRecords.existsById("SIM-02")).thenReturn(true);

        service.processApplicationAsync(request("SIM-02"));

        verify(agreementRecords, never()).save(any());
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void aRaceThatSlipsPastExistsByIdIsCaughtByTheUniqueKeyAndSkipsTheWorker() {
        // Two /execute calls for the same id can both see existsById == false before either has
        // committed. The primary key is the real guarantee; this proves the loser backs off rather
        // than dispatching a second worker.
        when(agreementRecords.existsById("SIM-03")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(agreementRecords).save(any(AgreementRecord.class));

        service.processApplicationAsync(request("SIM-03"));

        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void aFailureIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        // The failure mode this guard exists for: a module that throws never reports, and the
        // orchestrator then waits out its 30s timeout and ends the journey FAILED with nothing to
        // explain it. REFERRED with a reason is far more useful than silence.
        when(agreementRecords.existsById("SIM-04")).thenReturn(false);
        when(agreementRecords.findById("SIM-04")).thenReturn(Optional.empty());

        service.processApplicationAsync(request("SIM-04"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-04"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("no AgreementRecord row for SIM-04");
    }

    @Test
    void theBoardShowsWhatWasStored() {
        AgreementRecord row = new AgreementRecord("SIM-01", AgreementStatus.GENERATING);
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .thenReturn(java.util.List.of(row));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("GENERATING");
                });
    }
}

