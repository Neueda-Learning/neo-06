package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.OverrideRequest;
import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.dto.ResendRequest;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseSearchService;
import com.neobank.module.service.CaseService;
import com.neobank.module.service.OverrideService;
import com.neobank.module.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The case surface: UC02's detail read, UC01's search, UC03's applicant proxy, UC04's resend, and
 * UC08's override. See each use case's doc under
 * {@code module-06-agreement-management-docs/} for its exact contract.
 */
@RestController
@RequestMapping("/cases")
public class CasesController {

    private final CaseService cases;
    private final CaseSearchService search;
    private final ApplicantService applicants;
    private final QueueService queue;
    private final OverrideService overrides;

    public CasesController(CaseService cases, CaseSearchService search, ApplicantService applicants,
                           QueueService queue, OverrideService overrides) {
        this.cases = cases;
        this.search = search;
        this.applicants = applicants;
        this.queue = queue;
        this.overrides = overrides;
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView getCase(@PathVariable String applicationId) {
        return cases.getCase(applicationId);
    }

    /** UC01: {@code GET /cases?q=&status=&limit=10}. */
    @GetMapping
    public CaseSearchResult searchCases(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "10") int limit) {
        return search.search(q, status, limit);
    }

    /** UC03: {@code GET /cases/{id}/applicant}. */
    @GetMapping("/{applicationId}/applicant")
    public ApplicantView getApplicant(@PathVariable String applicationId) {
        return applicants.getApplicant(applicationId);
    }

    /** UC04: {@code POST /cases/{id}/resend}. */
    @PostMapping("/{applicationId}/resend")
    public QueueEntryView resend(@PathVariable String applicationId,
                                 @Valid @RequestBody ResendRequest request) {
        return queue.resend(applicationId, request.operator());
    }

    /** UC08: {@code POST /cases/{id}/override}. */
    @PostMapping("/{applicationId}/override")
    public QueueEntryView override(@PathVariable String applicationId,
                                   @Valid @RequestBody OverrideRequest request) {
        return overrides.override(applicationId, request);
    }
}

