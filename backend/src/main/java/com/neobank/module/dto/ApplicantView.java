package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

/**
 * What {@code GET /cases/{applicationId}/applicant} returns — see
 * {@code module-06-agreement-management-docs/uc-03-view-applicant.md}'s "Contract" section.
 *
 * <p>Flattened straight off the orchestrator's {@link Application} — nothing here is ever
 * persisted (AC3: "one copy of the truth, owned by the orchestrator"). When the orchestrator
 * cannot be reached, {@code retryable} is {@code true} and every personal field is {@code null}
 * (AC4) — the response is still a {@code 200}, never a {@code 404}/{@code 500}, so a sidebar
 * failure never takes the rest of the case-detail screen down with it.</p>
 */
public record ApplicantView(
        String applicationId,
        String fullName,
        String email,
        String mobile,
        String productCode,
        Boolean termsAccepted,
        boolean retryable) {

    public static ApplicantView of(String applicationId, Application application) {
        Application.Applicant applicant = application.applicant();
        Application.Product product = application.product();
        Application.Consents consents = application.consents();
        return new ApplicantView(
                applicationId,
                applicant == null ? null : applicant.fullName(),
                applicant == null ? null : applicant.email(),
                applicant == null ? null : applicant.mobile(),
                product == null ? null : product.productCode(),
                consents == null ? null : consents.termsAccepted(),
                false);
    }

    public static ApplicantView retryable(String applicationId) {
        return new ApplicantView(applicationId, null, null, null, null, null, true);
    }
}
