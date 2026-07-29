package com.neobank.module.service;

/**
 * The case exists but the signature event cannot be applied to it right now — a stale envelope, a
 * state other than {@code PENDING}, or a contradicting terminal decision. Always {@code 409} (see
 * {@code GlobalExceptionHandler}): the case is real, the request is just out of turn.
 */
public class SignatureEventConflictException extends RuntimeException {

    public SignatureEventConflictException(String message) {
        super(message);
    }
}
