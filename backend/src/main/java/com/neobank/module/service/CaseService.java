package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.integrations.orchestrator.OrchestratorApplicationClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
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
 * <p>Also UC03 — View Applicant: {@link #getApplicant} proxies the orchestrator's copy of the
 * applicant, live, and never stores it (see
 * {@code module-06-agreement-management-docs/uc-03-view-applicant.md}).</p>
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;
    private final OrchestratorApplicationClient orchestratorApplications;

    public CaseService(AgreementRecordRepository agreementRecords,
                       AgreementStatusHistoryRepository history,
                       OrchestratorApplicationClient orchestratorApplications) {
        this.agreementRecords = agreementRecords;
        this.history = history;
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
