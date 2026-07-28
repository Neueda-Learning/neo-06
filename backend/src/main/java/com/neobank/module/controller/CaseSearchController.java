package com.neobank.module.controller;

import com.neobank.module.dto.CaseSearchResultView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.service.CaseSearchService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>UC 01 · Search Cases — the operator board's read API.</h2>
 *
 * <p>{@code GET /api/v1/cases?q=…&status=…&limit=10} — find agreement cases by application id
 * or applicant name. Distinct from {@link ApplicationController}'s {@code /api/v1/applications}:
 * that path is the UC 00 contract surface (POST in, list out), this one is the UC 01 search
 * board with its own contract shape ({@link CaseSearchResultView}).</p>
 *
 * <p>The response is a bare JSON array, matching the UC 01 contract. The "more — refine your
 * search" signal from AC2 is sent as the {@code X-More-Results: true} response header so the
 * body shape stays exactly what the contract pins — a wrapper object would have broken the
 * shape for the orchestrator's own contract checks, and a header is what a board polls for
 * "is there a next page".</p>
 *
 * <p>The default is empty (AC1): no {@code q}, no rows. Id search is local; name search goes
 * through the orchestrator (see {@link CaseSearchService}). Status filter includes
 * {@code GENERATING} (AC6) — operators see the internal status. No matches is {@code 200 + []}
 * (AC7), never a {@code 500}, even when the orchestrator is down.</p>
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseSearchController {

    private final CaseSearchService search;

    public CaseSearchController(CaseSearchService search) {
        this.search = search;
    }

    /**
     * Search the agreement board.
     *
     * @param q      application id substring, or applicant name. Blank → empty board (AC1).
     * @param status optional lifecycle filter; {@code GENERATING} is allowed (AC6).
     * @param limit  page size, capped at 10 by the service (AC2). Defaults to 10.
     */
    @GetMapping
    public List<CaseSearchResultView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AgreementStatus status,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletResponse response) {
        CaseSearchService.SearchResult result = search.search(q, status, limit);
        if (result.more()) {
            // AC2: an eleventh match means "more — refine your search". Header, not a wrapper,
            // so the body stays the bare array the contract pins.
            response.setHeader("X-More-Results", "true");
        }
        return result.rows();
    }
}
