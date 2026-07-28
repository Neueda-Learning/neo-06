package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC 00's two halves, tested separately: {@link ApplicationService#processApplicationAsync} (the
 * durable row + idempotency + hand-off) and {@link ApplicationService#decide} (the one decision
 * this use case owns — the consent gate).
 *
 * <p>No Spring, no database, no HTTP — same style as the placeholder test it replaces: the
 * service takes a request and calls two collaborators, so each test is a handful of lines.</p>
 */
class ApplicationServiceTest {

    private AgreementRecordRepository agreementRecords;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        // Runnable::run — the hand-off happens inline, so there is nothing to wait for.
        service = new ApplicationService(Runnable::run, agreementRecords, orchestrator);
        when(agreementRecords.save(any(AgreementRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ApplicationRequest request(String id, Boolean termsAccepted) {
        Application.Consents consents = termsAccepted == null ? null
                : new Application.Consents(termsAccepted, null, null);
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", null, null, null, null,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null, consents);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void firstExecuteInsertsOneGeneratingRowBeforeHandingOffTheDecision() {
        when(agreementRecords.existsById("SIM-01")).thenReturn(false);

        service.processApplicationAsync(request("SIM-01", null));

        ArgumentCaptor<AgreementRecord> saved = ArgumentCaptor.forClass(AgreementRecord.class);
        verify(agreementRecords).save(saved.capture());
        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getStatus()).isEqualTo(AgreementStatus.GENERATING);
    }

    @Test
    void aRepeatedExecuteForTheSameIdInsertsNoSecondRowAndDoesNotReprocess() {
        // Same request twice, same result once: the row already exists, so neither a second
        // insert nor a second decision may happen.
        when(agreementRecords.existsById("SIM-02")).thenReturn(true);

        service.processApplicationAsync(request("SIM-02", null));

        verify(agreementRecords, never()).save(any());
        verifyNoInteractions(orchestrator);
    }

    @Test
    void consentGateFalseDeclinesTheCaseAndReportsRejected() {
        AgreementRecord row = new AgreementRecord("SIM-03", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-03")).thenReturn(Optional.of(row));

        service.decide(request("SIM-03", false));

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.DECLINED);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REJECTED), any());
    }

    @Test
    void consentAcceptedLeavesTheRowGeneratingAndReportsNothingYet() {
        // The happy path (PDF, envelope, PENDING) is the decision engine's job, not UC 00's —
        // so accepted consent has nothing more to do here yet.
        service.decide(request("SIM-04", true));

        verifyNoInteractions(orchestrator);
        verify(agreementRecords, never()).findById(any());
    }

    @Test
    void aMissingConsentsBlockIsTreatedAsNotYetGated() {
        service.decide(request("SIM-05", null));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void aFailureDuringDecideIsReportedReferredRatherThanLeavingTheJourneyToTimeOut() {
        // The failure mode this guard exists for: a module that throws never reports, and the
        // orchestrator then waits out its timeout and ends the journey with nothing to explain
        // it. REFERRED with a reason is far more useful than silence.
        when(agreementRecords.findById("SIM-06"))
                .thenThrow(new IllegalStateException("database on fire"));

        service.decide(request("SIM-06", false));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-06"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
    }

    @Test
    void theBoardShowsWhatWasStored() {
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .thenReturn(List.of(new AgreementRecord("SIM-01", AgreementStatus.GENERATING)));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("GENERATING");
                });
    }
}
