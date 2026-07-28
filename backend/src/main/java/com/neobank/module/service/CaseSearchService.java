package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchResultView;
import com.neobank.module.integrations.applicantlookup.ApplicantLookupClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 01 · Search Cases — find an agreement case by application id or applicant name.</h2>
 *
 * <p>The board is empty by default (AC1): no {@code q}, no rows fetched, the UI invites a
 * search. With a query, two paths run, in this order:</p>
 *
 * <ol>
 *   <li><b>Id search — local only.</b> Substring match on {@code applicationId} against this
 *       module's own table. The schema has no name column, so this path never leaves the
 *       service. (AC3: "Search by applicationId works entirely from the local table".)</li>
 *   <li><b>Name search — orchestrator first.</b> If id search found nothing, the query is
 *       treated as a name: {@link ApplicantLookupClient} resolves it to a list of ids via the
 *       orchestrator's {@code GET /api/v1/applications?name=}, and this service loads the
 *       local rows for those ids. Nothing about the applicant is ever persisted. (AC3: "search
 *       by name resolves ids through the orchestrator first".)</li>
 * </ol>
 *
 * <p>The cap is 10 (AC2: "at most 10 matches, newest first"). To flag "more — refine your
 *       search" without changing the contract's bare-array response shape, the service fetches
 *       {@code limit + 1} rows; an extra row means there is an eleventh, so the controller
 *       sets {@code X-More-Results: true} on the response and the eleventh is trimmed.</p>
 *
 * <p>Status filter (AC6) covers all five lifecycle statuses including internal {@code GENERATING}
 * — operators see it even though no callback ever names it. A {@code null} status means "all".</p>
 *
 * <p>Failures from the orchestrator are already swallowed by {@link ApplicantLookupClient} —
 * name search returns empty, id search is unaffected, and the call never becomes a
 * {@code 500} (AC7).</p>
 */
@Service
public class CaseSearchService {

    private static final Logger log = LoggerFactory.getLogger(CaseSearchService.class);

    /** UC 01 AC2's hard cap — the board never shows more than ten rows. */
    static final int MAX_LIMIT = 10;

    private final AgreementRecordRepository agreementRecords;
    private final ApplicantLookupClient applicantLookup;

    public CaseSearchService(AgreementRecordRepository agreementRecords,
                             ApplicantLookupClient applicantLookup) {
        this.agreementRecords = agreementRecords;
        this.applicantLookup = applicantLookup;
    }

    /**
     * Search the board. Returns the matched rows (capped at {@code limit}) and a flag saying
     * whether an eleventh match existed — the controller turns that into the
     * {@code X-More-Results: true} header.
     *
     * @param q      the search term (application id substring, or applicant name). Blank → empty.
     * @param status optional status filter; {@code null} means all statuses including GENERATING.
     * @param limit  the page size requested by the caller; capped to {@link #MAX_LIMIT}.
     */
    @Transactional(readOnly = true)
    public SearchResult search(String q, AgreementStatus status, int limit) {
        if (q == null || q.isBlank()) {
            // AC1: empty by default — no query, no rows fetched.
            return SearchResult.empty();
        }

        int pageSize = clamp(limit);
        // Fetch one extra so "more" is detectable without a second query.
        Pageable pageable = PageRequest.of(0, pageSize + 1,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("applicationId")));

        List<AgreementRecord> rows = searchByIdOrName(q.trim(), status, pageable);
        boolean more = rows.size() > pageSize;
        List<CaseSearchResultView> trimmed = rows.stream()
                .limit(pageSize)
                .map(CaseSearchResultView::of)
                .toList();
        return new SearchResult(trimmed, more);
    }

    /**
     * Id search first; name search only as a fallback. The split is what UC 01 AC3 asks for:
     * id search is purely local (no orchestrator call when the term already matches an id), and
     * name search only runs when id search found nothing — so an obvious id query like
     * {@code "SIM-01"} never hits the orchestrator.
     */
    private List<AgreementRecord> searchByIdOrName(String q, AgreementStatus status, Pageable pageable) {
        List<AgreementRecord> local = agreementRecords.searchByApplicationId(q, status, pageable);
        if (!local.isEmpty()) {
            return local;
        }
        // Id search found nothing — try the query as a name via the orchestrator.
        List<String> ids = applicantLookup.findApplicationIdsByName(q);
        if (ids.isEmpty()) {
            return List.of();
        }
        return agreementRecords.searchByApplicationIds(ids, status, pageable);
    }

    private static int clamp(int limit) {
        if (limit <= 0) {
            return MAX_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** What {@link CaseSearchService#search} returns — the rows plus the "more" flag. */
    public record SearchResult(List<CaseSearchResultView> rows, boolean more) {
        static SearchResult empty() {
            return new SearchResult(List.of(), false);
        }
    }
}
