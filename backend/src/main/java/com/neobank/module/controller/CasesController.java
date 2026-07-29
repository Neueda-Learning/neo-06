package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCommand;
import com.neobank.module.service.CaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC02 — Review Agreement: {@code GET /cases/{applicationId}}. Read-only and idempotent — see
 * {@code module-06-agreement-management-docs/uc-02-review-agreement.md}.
 *
 * <p>UC08 — Override Case: {@code POST /cases/{applicationId}/override}. The ONE permitted
 * mutation outside the lifecycle — see
 * {@code module-06-agreement-management-docs/uc-08-override-case.md}.</p>
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

    /**
     * UC 08 — Override a case's workflow state with a reason.
     *
     * <p>Legal moves: PENDING → DECLINED, EXPIRED → DECLINED, DECLINED → PENDING.
     * SIGNED is never a legal target (AC 3).</p>
     */
    @PostMapping("/{applicationId}/override")
    public CaseDetailView override(@PathVariable String applicationId,
                                   @RequestBody OverrideCommand cmd) {
        return cases.override(applicationId, cmd);
    }

    @GetMapping("/{applicationId}/applicant")
    public ApplicantView getApplicant(@PathVariable String applicationId) {
        return cases.getApplicant(applicationId);
    }
}