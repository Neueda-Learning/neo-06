package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.CaseSummaryView;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <h2>UC 01 · Search Cases — the Agreement Board's search.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-01-search-cases.md}. Empty by default
 * (AC1); {@code applicationId} is matched locally; a name match resolves through
 * {@link ApplicantService}'s orchestrator proxy — the platform's own {@code ≤10 hydration calls}
 * budget (the same one the board's name column spends rendering) is exactly what bounds this
 * search's cost, since there is no name-search endpoint on the fixed orchestrator contract.</p>
 */
@Service
public class CaseSearchService {

    private static final Logger log = LoggerFactory.getLogger(CaseSearchService.class);

    /** The platform's own hydration budget — see the docs' "≤10 hydration calls" rule. */
    private static final int HYDRATION_BUDGET = 10;

    private final AgreementRecordRepository agreementRecords;
    private final ApplicantService applicants;

    public CaseSearchService(AgreementRecordRepository agreementRecords, ApplicantService applicants) {
        this.agreementRecords = agreementRecords;
        this.applicants = applicants;
    }

    /**
     * @param q      the search term — an {@code applicationId} substring or an applicant name;
     *               blank means "no query" (AC1) and always returns an empty result
     * @param status optional status filter, e.g. {@code GENERATING} (AC6) — {@code null}/blank
     *               means "any status"
     * @param limit  how many rows to return, capped by the caller (default 10, AC2)
     */
    public CaseSearchResult search(String q, String status, int limit) {
        if (q == null || q.isBlank()) {
            return new CaseSearchResult(List.of(), false);
        }
        AgreementStatus statusFilter = parseStatus(status);
        List<AgreementRecord> candidates = agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc()
                .stream()
                .filter(r -> statusFilter == null || r.getStatus() == statusFilter)
                .toList();

        String needle = q.toLowerCase();
        Set<String> matchedIds = new LinkedHashSet<>();
        for (AgreementRecord record : candidates) {
            if (record.getApplicationId().toLowerCase().contains(needle)) {
                matchedIds.add(record.getApplicationId());
            }
        }

        int budget = HYDRATION_BUDGET;
        for (AgreementRecord record : candidates) {
            if (budget <= 0) {
                break;
            }
            if (matchedIds.contains(record.getApplicationId())) {
                continue;
            }
            budget--;
            try {
                ApplicantView applicant = applicants.getApplicant(record.getApplicationId());
                if (applicant.fullName() != null
                        && applicant.fullName().toLowerCase().contains(needle)) {
                    matchedIds.add(record.getApplicationId());
                }
            } catch (OrchestratorUnavailableException e) {
                // AC7: the orchestrator being down degrades name search for this one candidate —
                // it never fails the whole request.
                log.warn("name search skipped {} — orchestrator unreachable: {}",
                        record.getApplicationId(), e.getMessage());
            }
        }

        List<CaseSummaryView> matched = candidates.stream()
                .filter(r -> matchedIds.contains(r.getApplicationId()))
                .map(CaseSummaryView::of)
                .toList();

        boolean more = matched.size() > limit;
        return new CaseSearchResult(matched.stream().limit(limit).toList(), more);
    }

    private static AgreementStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AgreementStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown status: " + status);
        }
    }
}
