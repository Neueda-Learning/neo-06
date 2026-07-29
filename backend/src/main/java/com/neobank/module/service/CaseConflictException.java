package com.neobank.module.service;

/**
 * A case exists but the requested action cannot be applied to it right now — used across UC 04
 * (resend), UC 05 (consent-gate declined document), and UC 08 (illegal override target). Always
 * {@code 409} (see {@code GlobalExceptionHandler}): the case is real, the request is just out of
 * turn or targets a status that is never legal from here.
 */
public class CaseConflictException extends RuntimeException {

    public CaseConflictException(String message) {
        super(message);
    }
}
