package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.service.CaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC02 — Review Agreement: {@code GET /cases/{applicationId}}. Read-only and idempotent — see
 * {@code module-06-agreement-management-docs/uc-02-review-agreement.md}.
 *
 * <p>UC03 — View Applicant: {@code GET /cases/{applicationId}/applicant}, the platform-wide proxy
 * every module ships — see
 * {@code module-06-agreement-management-docs/uc-03-view-applicant.md}. Always {@code 200}, even
 * when the orchestrator is unreachable (AC4) — never a {@code 404}/{@code 500} for this path.</p>
 */
@RestController
@RequestMapping("/cases")
public class CasesController {

    private final CaseService cases;

    public CasesController(CaseService cases) {
        this.cases = cases;
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView getCase(@PathVariable String applicationId) {
        return cases.getCase(applicationId);
    }

    @GetMapping("/{applicationId}/applicant")
    public ApplicantView getApplicant(@PathVariable String applicationId) {
        return cases.getApplicant(applicationId);
    }
}
