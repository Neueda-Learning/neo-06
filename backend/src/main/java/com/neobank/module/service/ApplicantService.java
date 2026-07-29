package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.springframework.stereotype.Service;

/**
 * <h2>UC 03 · View Applicant — the standard application-fetch proxy every module ships.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-03-view-applicant.md}. Zero writes, zero
 * copies: this class never touches MySQL — it only proxies
 * {@link OrchestratorClient#getApplication} and narrows the response to the subset the sidebar
 * needs (AC1). Also reused, at a bounded budget, by {@link CaseSearchService}'s name search and
 * by the board/queue's live name hydration.</p>
 */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;

    public ApplicantService(OrchestratorClient orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * @throws OrchestratorUnavailableException (AC4) if the orchestrator cannot be reached — the
     *         caller degrades gracefully (a retryable sidebar error, or simply no name match)
     *         rather than failing the whole page.
     */
    public ApplicantView getApplicant(String applicationId) {
        try {
            Application application = orchestrator.getApplication(applicationId);
            return ApplicantView.of(application);
        } catch (OrchestratorClient.OrchestratorFetchException e) {
            throw new OrchestratorUnavailableException(
                    "applicant lookup for " + applicationId + " failed: " + e.getMessage(), e);
        }
    }
}
