package com.neobank.module.service;

/**
 * UC 04's {@code 409}: {@code POST /cases/{id}/resend} for a case that is not
 * {@code PENDING}/{@code EXPIRED} — already {@code SIGNED}/{@code DECLINED} (AC5), or still
 * {@code GENERATING} (no envelope has ever been sent, so there is nothing to resend).
 */
public class ResendNotAllowedException extends RuntimeException {

    public ResendNotAllowedException(String message) {
        super(message);
    }
}
