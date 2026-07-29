package com.neobank.module.dto;

import java.util.List;

/**
 * {@code GET /cases}'s response envelope — see
 * {@code module-06-agreement-management-docs/uc-01-search-cases.md} AC2: "an 11th match means the
 * response flags 'more — refine your search'." {@code items} is already capped at the requested
 * {@code limit}; {@code more} tells the UI whether to show that hint.
 */
public record CaseSearchResult(List<CaseSummaryView> items, boolean more) {
}
