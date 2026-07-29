package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCommand;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationClient;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OverrideLogRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * UC02 — Review Agreement: assemble a case's stored terms and its full timeline. Read-only; it
 * computes nothing (see the doc's Build notes: "Reading replays — it never re-decides").
 *
 * <p>Also hosts UC 08 — Override Case: the ONE permitted mutation outside the lifecycle.
 * Legal moves: PENDING → DECLINED, EXPIRED → DECLINED, DECLINED → PENDING.</p>
 *
 * <p>Also UC03 — View Applicant: {@link #getApplicant} proxies the orchestrator's copy of the
 * applicant, live, and never stores it (see
 * {@code module-06-agreement-management-docs/uc-03-view-applicant.md}).</p>
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;
    private final OverrideLogRepository overrideLogs;
    private final OrchestratorClient orchestrator;
    private final OrchestratorApplicationClient orchestratorApplications;

    public CaseService(AgreementRecordRepository agreementRecords,
                       AgreementStatusHistoryRepository history,
                       OverrideLogRepository overrideLogs,
                       OrchestratorClient orchestrator,
                       OrchestratorApplicationClient orchestratorApplications) {
        this.agreementRecords = agreementRecords;
        this.history = history;
        this.overrideLogs = overrideLogs;
        this.orchestrator = orchestrator;
        this.orchestratorApplications = orchestratorApplications;
    }

    /**
     * @throws NoSuchElementException when no {@link AgreementRecord} exists for the id (AC7 —
     *         {@code GlobalExceptionHandler} turns this into a {@code 404}, never a {@code 500}).
     */
    @Transactional(readOnly = true)
    public CaseDetailView getCase(String applicationId) {
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));

        var timeline = history.findByApplicationIdOrderByOccurredAtAsc(applicationId).stream()
                .map(TimelineEntryView::of)
                .toList();

        return CaseDetailView.of(record, timeline);
    }

    /**
     * UC 08 — Override Case: change a case's workflow state with a reason.
     *
     * <p>Legal moves: PENDING → DECLINED (bank stops a live offer), EXPIRED → DECLINED (abandon),
     * DECLINED → PENDING (revive). SIGNED is never a legal target (AC 3).</p>
     *
     * @throws NoSuchElementException when no case exists for the id (404)
     * @throws OverrideNotAllowedException when the case is SIGNED (409) or request is invalid (400)
     */
    @Transactional
    public CaseDetailView override(String applicationId, OverrideCommand cmd) {
        // Validate request (AC 2)
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new OverrideNotAllowedException("reason is mandatory");
        }
        if (cmd.operator() == null || cmd.operator().isBlank()) {
            throw new OverrideNotAllowedException("operator is mandatory");
        }

        AgreementStatus newStatus;
        try {
            newStatus = AgreementStatus.valueOf(cmd.newStatus());
        } catch (IllegalArgumentException e) {
            throw new OverrideNotAllowedException(
                    "newStatus must be PENDING or DECLINED, not " + cmd.newStatus());
        }

        if (newStatus == AgreementStatus.SIGNED) {
            throw new OverrideNotAllowedException("SIGNED is never a legal override target");
        }

        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));

        // AC 3: SIGNED cases → 409, exact message
        if (record.getStatus() == AgreementStatus.SIGNED) {
            throw new OverrideNotAllowedException(
                    "SIGNED is never overridden — the contract is in force");
        }

        // Check for idempotent replay (before validating legal moves)
        if (record.getStatus() == newStatus) {
            log.info("REPLAY override {} → {} on {} — already in that state, nothing to do",
                    record.getStatus(), newStatus, applicationId);
            return getCase(applicationId);
        }

        // Validate legal moves (AC 6)
        validateLegalMove(record.getStatus(), newStatus);

        AgreementStatus oldStatus = record.getStatus();
        Instant now = Instant.now();

        // Update the record
        record.changeStatus(newStatus);
        agreementRecords.save(record);

        // Write override_log (AC 4)
        overrideLogs.save(new OverrideLog(applicationId, oldStatus, newStatus,
                cmd.reason(), cmd.operator(), now));

        // Write history row (AC 4)
        history.save(new AgreementStatusHistory(applicationId, oldStatus, newStatus,
                "OVERRIDE", cmd.operator(), now));

        // Send callback with status local-manual (AC 5)
        Decision decision = decisionFor(newStatus);
        String comment = "manual override: " + cmd.reason();
        orchestrator.applicationStatusUpdate(applicationId, decision, comment);

        log.info("OVERRIDDEN {} {} → {} by {}", applicationId, oldStatus, newStatus, cmd.operator());

        return getCase(applicationId);
    }

    private void validateLegalMove(AgreementStatus from, AgreementStatus to) {
        // AC 6: Legal moves are PENDING → DECLINED, EXPIRED → DECLINED, DECLINED → PENDING
        // GENERATING is internal and should not be overridden
        boolean legal = switch (from) {
            case PENDING -> to == AgreementStatus.DECLINED;
            case EXPIRED -> to == AgreementStatus.DECLINED || to == AgreementStatus.PENDING;
            case DECLINED -> to == AgreementStatus.PENDING;
            case GENERATING -> false; // GENERATING is internal, cannot be overridden
            case SIGNED -> false; // Already handled above, but explicit for completeness
        };

        if (!legal) {
            throw new OverrideNotAllowedException(
                    "cannot override from " + from + " to " + to);
        }
    }

    private Decision decisionFor(AgreementStatus status) {
        // Map internal status to orchestrator's Decision
        // PENDING = in-progress (REFERRED for local-manual), DECLINED = REJECTED
        return status == AgreementStatus.PENDING ? Decision.REFERRED : Decision.REJECTED;
    }

    /**
     * UC03 — View Applicant. Never persisted (AC3): the DTO is built straight off the
     * orchestrator's response and no repository is touched.
     *
     * <p>An unreachable orchestrator is NOT reported as an error (AC4) — it comes back as a
     * {@code retryable} view instead, so the sidebar can show a retryable state while the rest of
     * the case-detail screen (a separate call, {@link #getCase}) keeps rendering from local
     * data.</p>
     */
    public ApplicantView getApplicant(String applicationId) {
        try {
            var application = orchestratorApplications.getApplication(applicationId);
            return ApplicantView.of(applicationId, application);
        } catch (RestClientException e) {
            log.warn("Applicant lookup failed for {} — orchestrator unreachable, sidebar goes "
                    + "retryable: {}", applicationId, e.toString());
            return ApplicantView.retryable(applicationId);
        }
    }
}