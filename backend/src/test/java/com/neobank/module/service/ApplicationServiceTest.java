package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * UC 00's two halves, tested separately: {@link ApplicationService#processApplicationAsync} (the
 * durable row + idempotency + hand-off) and {@link ApplicationService#decide} (the consent gate
 * plus UC 07's send-for-signature wiring).
 *
 * <p>No Spring, no database, no HTTP — the service takes a request and calls its collaborators, so
 * each test is a handful of lines.</p>
 */
class ApplicationServiceTest {

    private AgreementRecordRepository agreementRecords;
    private OrchestratorClient orchestrator;
    private AgreementDocumentComposer agreementDocuments;
    private EsignProvider esignProvider;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        // Runnable::run — the hand-off happens inline, so there is nothing to wait for.
        agreementDocuments = mock(AgreementDocumentComposer.class);
        esignProvider = mock(EsignProvider.class);
        service = new ApplicationService(Runnable::run, agreementRecords, orchestrator,
                agreementDocuments, esignProvider);
        when(agreementRecords.save(any(AgreementRecord.class))).thenAnswer(call -> call.getArgument(0));
        // Default: a fresh envelope, INSTANT/SIGN, no demo expiry override — most tests don't care.
        when(esignProvider.registerEnvelope(any(), any(), any())).thenReturn(new EnvelopeRegistration(
                "env-test", new EsignConfigView(EsignMode.INSTANT, 0, EsignOutcome.SIGN, null)));
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

        // Not declined (no consents block yet), so the placeholder agreement PDF is generated,
        // the case moves toward PENDING, and the orchestrator is told ACCEPTED.
        verify(agreementDocuments).compose("SIM-01");
        verify(orchestrator).applicationStatusUpdate(eq("SIM-01"), eq(Decision.ACCEPTED), any());
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
    void aRaceThatSlipsPastExistsByIdIsCaughtByTheUniqueKeyAndSkipsTheDecision() {
        // Two /execute calls for the same id can both see existsById == false before either has
        // committed. The primary key is the real guarantee; this proves the loser backs off rather
        // than dispatching a second decision.
        when(agreementRecords.existsById("SIM-07")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(agreementRecords).save(any(AgreementRecord.class));

        service.processApplicationAsync(request("SIM-07", null));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void consentGateFalseDeclinesTheCaseAndReportsRejected() {
        AgreementRecord row = new AgreementRecord("SIM-03", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-03")).thenReturn(Optional.of(row));

        service.decide(request("SIM-03", false));

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.DECLINED);
        // Regression guard: updateStatus's @Transactional does nothing on a self-invoked call
        // (bypasses Spring's proxy), so the mutation must be persisted with an explicit save() —
        // a mock can't catch a missing flush the way the real database did.
        verify(agreementRecords).save(row);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REJECTED), any());
    }

    @Test
    void consentAcceptedGeneratesTheDocumentMovesToPendingAndReportsAccepted() {
        AgreementRecord row = new AgreementRecord("SIM-04", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-04")).thenReturn(Optional.of(row));

        service.decide(request("SIM-04", true));

        verify(agreementDocuments).compose("SIM-04");
        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        verify(agreementRecords).save(row);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-04"), eq(Decision.ACCEPTED), any());
    }

    @Test
    void sendingForSignatureStampsTheEnvelopeAndExpiryBeforeReportingAndPlayingAutoMode() {
        // UC 07: the envelope, sentAt and expiresAt all land on the row, in that order relative to
        // the orchestrator callback and the auto-mode playback — playAutoMode must never run
        // before the row is actually PENDING (see the class javadoc on sendForSignature).
        AgreementRecord row = new AgreementRecord("SIM-08", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-08")).thenReturn(Optional.of(row));
        when(agreementDocuments.compose("SIM-08")).thenReturn("deadbeef");
        EnvelopeRegistration registration = new EnvelopeRegistration("env-abc123",
                new EsignConfigView(EsignMode.SILENT, 0, EsignOutcome.SIGN, 30));
        when(esignProvider.registerEnvelope("SIM-08", "deadbeef", "Maria Nowak"))
                .thenReturn(registration);

        service.decide(request("SIM-08", true));

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(row.getEnvelopeId()).isEqualTo("env-abc123");
        assertThat(row.getSentAt()).isNotNull();
        // demoExpirySeconds=30 on the snapshot taken at registration → expiresAt ~30s after sentAt.
        assertThat(row.getExpiresAt()).isEqualTo(row.getSentAt().plusSeconds(30));
        verify(orchestrator).applicationStatusUpdate(eq("SIM-08"), eq(Decision.ACCEPTED), any());
        verify(esignProvider).playAutoMode("SIM-08", registration);
    }

    @Test
    void anUnreachableEsignProviderStillMovesTheCaseToPendingAndRefersIt() {
        // AC 7: the send leg fails, but the document exists — the case is un-sent, not un-decided.
        AgreementRecord row = new AgreementRecord("SIM-09", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-09")).thenReturn(Optional.of(row));
        when(esignProvider.registerEnvelope(any(), any(), any()))
                .thenThrow(new IllegalStateException("mock down"));

        service.decide(request("SIM-09", true));

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(row.getEnvelopeId()).isNull();
        verify(orchestrator).applicationStatusUpdate(eq("SIM-09"), eq(Decision.REFERRED),
                org.mockito.ArgumentMatchers.contains("AGR_PROVIDER_UNAVAILABLE"));
        verify(esignProvider, never()).playAutoMode(any(), any());
    }

    @Test
    void aMissingConsentsBlockIsTreatedTheSameAsAcceptedForNow() {
        // No consents block yet (not the same as an explicit decline): treated like accepted —
        // the document is generated and the case moves to PENDING.
        AgreementRecord row = new AgreementRecord("SIM-05", AgreementStatus.GENERATING);
        when(agreementRecords.findById("SIM-05")).thenReturn(Optional.of(row));

        service.decide(request("SIM-05", null));

        verify(agreementDocuments).compose("SIM-05");
        assertThat(row.getStatus()).isEqualTo(AgreementStatus.PENDING);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-05"), eq(Decision.ACCEPTED), any());
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
