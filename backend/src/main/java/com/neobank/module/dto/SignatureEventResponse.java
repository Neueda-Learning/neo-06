package com.neobank.module.dto;

import com.neobank.module.model.AgreementStatus;

/**
 * The {@code 200} body {@code SignatureEventController} answers with — this module's own read
 * shape, not fixed by any contract. {@code replay} is what lets a caller (or a test) tell an
 * event that actually moved the case apart from one that landed on an already-decided case and
 * changed nothing (AC 3): same request twice, same result once, but visibly so.
 */
public record SignatureEventResponse(String applicationId, String status, boolean replay) {

    public static SignatureEventResponse applied(String applicationId, AgreementStatus status) {
        return new SignatureEventResponse(applicationId, status.name(), false);
    }

    public static SignatureEventResponse replay(String applicationId, AgreementStatus status) {
        return new SignatureEventResponse(applicationId, status.name(), true);
    }
}
