package com.neobank.module.service;

/**
 * The {@code applicationId} is a real {@link com.neobank.module.model.AgreementRecord}, but no
 * {@link com.neobank.module.model.OfferDocument} was ever generated for it — the consent-gate
 * case (see {@code prompts/uc-05-prompt.md}, AC7): {@code termsAccepted=false} moves straight to
 * {@code DECLINED} without generating anything. Always {@code 409} (see
 * {@code GlobalExceptionHandler}): the case is real, there is simply nothing to serve, and never
 * will be — it is not a {@code 404} (unknown id) or a transient state to retry.
 */
public class AgreementDocumentNotGeneratedException extends RuntimeException {

    public AgreementDocumentNotGeneratedException(String message) {
        super(message);
    }
}
