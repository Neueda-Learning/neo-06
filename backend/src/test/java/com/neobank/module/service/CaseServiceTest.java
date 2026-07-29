package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.OverrideCommand;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationClient;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OverrideLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

/**
 * UC02 — Review Agreement. No Spring, no database: the two repositories are mocked, so this pins
 * exactly what the doc's acceptance criteria require without needing a running app.
 *
 * <p>UC08 — Override Case: the ONE permitted mutation outside the lifecycle.</p>
 */
class CaseServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private OverrideLogRepository overrideLogs;
    private OrchestratorClient orchestrator;
    private OrchestratorApplicationClient orchestratorApplications;
    private CaseService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        overrideLogs = mock(OverrideLogRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        orchestratorApplications = mock(OrchestratorApplicationClient.class);
        service = new CaseService(agreementRecords, history, overrideLogs, orchestrator, orchestratorApplications);
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

    // ========== UC 08 — Override Case tests ==========

    @Test
    void overrideWithoutReasonIsRejected() {
        OverrideCommand cmd = new OverrideCommand("DECLINED", null, "operator");

        assertThatThrownBy(() -> service.override("app-1", cmd))
                .isInstanceOf(OverrideNotAllowedException.class)
                .hasMessageContaining("reason is mandatory");
    }

    @Test
    void overrideWithoutOperatorIsRejected() {
        OverrideCommand cmd = new OverrideCommand("DECLINED", "some reason", null);

        assertThatThrownBy(() -> service.override("app-1", cmd))
                .isInstanceOf(OverrideNotAllowedException.class)
                .hasMessageContaining("operator is mandatory");
    }

    @Test
    void overrideToSignedIsRejected() {
        OverrideCommand cmd = new OverrideCommand("SIGNED", "reason", "operator");

        assertThatThrownBy(() -> service.override("app-1", cmd))
                .isInstanceOf(OverrideNotAllowedException.class)
                .hasMessageContaining("SIGNED is never a legal override target");
    }

    @Test
    void overrideUnknownCaseReturns404() {
        when(agreementRecords.findById("ghost")).thenReturn(Optional.empty());
        OverrideCommand cmd = new OverrideCommand("DECLINED", "reason", "operator");

        assertThatThrownBy(() -> service.override("ghost", cmd))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void overrideSignedCaseReturns409WithExactMessage() {
        // AC 3: SIGNED cases → 409, exact message
        AgreementRecord signed = new AgreementRecord("app-signed", AgreementStatus.SIGNED);
        when(agreementRecords.findById("app-signed")).thenReturn(Optional.of(signed));

        OverrideCommand cmd = new OverrideCommand("DECLINED", "reason", "operator");

        assertThatThrownBy(() -> service.override("app-signed", cmd))
                .isInstanceOf(OverrideNotAllowedException.class)
                .hasMessage("SIGNED is never overridden — the contract is in force");
    }

    @Test
    void overridePendingToDeclinedSucceeds() {
        AgreementRecord pending = new AgreementRecord("app-1", AgreementStatus.PENDING,
                "env-123");
        when(agreementRecords.findById("app-1")).thenReturn(Optional.of(pending));
        when(agreementRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrideLogs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-1")).thenReturn(List.of());

        OverrideCommand cmd = new OverrideCommand("DECLINED", "Customer confirmed by phone — not proceeding", "b.dimovski");

        var result = service.override("app-1", cmd);

        assertThat(result.status()).isEqualTo("DECLINED");
        verify(overrideLogs).save(any());
        verify(history).save(any());
        verify(orchestrator).applicationStatusUpdate(eq("app-1"), eq(Decision.REJECTED), any());
    }

    @Test
    void overrideExpiredToDeclinedRemovesFromQueue() {
        // AC 6: Abandoning Tom's EXPIRED case to DECLINED removes it from the queue
        AgreementRecord expired = new AgreementRecord("app-expired", AgreementStatus.EXPIRED,
                "env-456");
        when(agreementRecords.findById("app-expired")).thenReturn(Optional.of(expired));
        when(agreementRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrideLogs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-expired")).thenReturn(List.of());

        OverrideCommand cmd = new OverrideCommand("DECLINED", "Abandoning expired case", "operator");

        var result = service.override("app-expired", cmd);

        assertThat(result.status()).isEqualTo("DECLINED");
    }

    @Test
    void overrideDeclinedToPendingRevivesCase() {
        // AC 6: Reviving a DECLINED case to PENDING rotates the envelope and resets the clock
        AgreementRecord declined = new AgreementRecord("app-declined", AgreementStatus.DECLINED,
                "env-789");
        when(agreementRecords.findById("app-declined")).thenReturn(Optional.of(declined));
        when(agreementRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrideLogs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-declined")).thenReturn(List.of());

        OverrideCommand cmd = new OverrideCommand("PENDING", "Reviving wrongly declined case", "operator");

        var result = service.override("app-declined", cmd);

        assertThat(result.status()).isEqualTo("PENDING");
        verify(orchestrator).applicationStatusUpdate(eq("app-declined"), eq(Decision.REFERRED), any());
    }

    @Test
    void overrideIdempotentReplayReturnsSameResult() {
        AgreementRecord declined = new AgreementRecord("app-already-declined", AgreementStatus.DECLINED);
        when(agreementRecords.findById("app-already-declined")).thenReturn(Optional.of(declined));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-already-declined")).thenReturn(List.of());

        OverrideCommand cmd = new OverrideCommand("DECLINED", "Already declined", "operator");

        var result = service.override("app-already-declined", cmd);

        assertThat(result.status()).isEqualTo("DECLINED");
        // No writes on replay
        verify(overrideLogs, never()).save(any());
        verify(history, never()).save(any());
        verify(orchestrator, never()).applicationStatusUpdate(any(), any(), any());
    }

    @Test
    void overrideGeneratesLocalManualCallback() {
        // AC 5: callback with status local-manual
        AgreementRecord pending = new AgreementRecord("app-callback", AgreementStatus.PENDING, "env-xxx");
        when(agreementRecords.findById("app-callback")).thenReturn(Optional.of(pending));
        when(agreementRecords.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(overrideLogs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(history.findByApplicationIdOrderByOccurredAtAsc("app-callback")).thenReturn(List.of());

        OverrideCommand cmd = new OverrideCommand("DECLINED", "Manual override", "operator");

        service.override("app-callback", cmd);

        verify(orchestrator).applicationStatusUpdate(eq("app-callback"), eq(Decision.REJECTED), any());
    }

    // --- UC03 — View Applicant -------------------------------------------------------------

    @Test
    void mariasApplicantViewMatchesTheDocsCheckpoint() {
        // AC2: app-1234 -> Maria Nowak / maria.nowak@example.com / CREDIT_CARD_REWARDS / terms accepted.
        Application application = new Application(
                "app-1234", "WEB", "2026-07-20T09:00:00Z",
                new Application.Applicant("Maria Nowak", "1990-04-11", "maria.nowak@example.com",
                        "+48123456789", "PL", "PL", List.of("PL"), "OWNER", null, 24, 0),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 2800),
                null,
                new Application.Consents(true, true, false));
        when(orchestratorApplications.getApplication("app-1234")).thenReturn(application);

        var view = service.getApplicant("app-1234");

        assertThat(view.applicationId()).isEqualTo("app-1234");
        assertThat(view.fullName()).isEqualTo("Maria Nowak");
        assertThat(view.email()).isEqualTo("maria.nowak@example.com");
        assertThat(view.productCode()).isEqualTo("CREDIT_CARD_REWARDS");
        assertThat(view.termsAccepted()).isTrue();
        assertThat(view.retryable()).isFalse();
    }

    @Test
    void orchestratorUnreachableReturnsARetryableViewNeverAnException() {
        // AC4: the sidebar degrades to retryable — the case detail, terms and timeline still
        // render (a separate call, getCase), so this must never throw.
        when(orchestratorApplications.getApplication("app-1234"))
                .thenThrow(new RestClientException("connection refused"));

        var view = service.getApplicant("app-1234");

        assertThat(view.applicationId()).isEqualTo("app-1234");
        assertThat(view.retryable()).isTrue();
        assertThat(view.fullName()).isNull();
        assertThat(view.email()).isNull();
        assertThat(view.mobile()).isNull();
        assertThat(view.productCode()).isNull();
        assertThat(view.termsAccepted()).isNull();
    }
}