package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

/**
 * {@code GET /cases/{id}/applicant}'s response shape — the subset of the orchestrator's
 * {@link Application} the Agreement Detail sidebar renders. See
 * {@code module-06-agreement-management-docs/uc-03-view-applicant.md} AC1.
 */
public record ApplicantView(
        String fullName,
        String email,
        String mobile,
        String productCode,
        Boolean termsAccepted) {

    public static ApplicantView of(Application application) {
        Application.Applicant applicant = application.applicant();
        Application.Product product = application.product();
        Application.Consents consents = application.consents();
        return new ApplicantView(
                applicant == null ? null : applicant.fullName(),
                applicant == null ? null : applicant.email(),
                applicant == null ? null : applicant.mobile(),
                product == null ? null : product.productCode(),
                consents == null ? null : consents.termsAccepted());
    }
}
