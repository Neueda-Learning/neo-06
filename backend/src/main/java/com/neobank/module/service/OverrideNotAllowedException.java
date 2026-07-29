package com.neobank.module.service;

/**
 * The case exists but the override cannot be applied — the case is {@code SIGNED},
 * or the target status is invalid. Always {@code 409} for SIGNED cases (AC 3) and
 * {@code 400} for invalid requests (AC 2).
 *
 * <p>Per UC 08 AC 3: "SIGNED is never overridden — the error body says exactly that."</p>
 */
public class OverrideNotAllowedException extends RuntimeException {

    public OverrideNotAllowedException(String message) {
        super(message);
    }
}