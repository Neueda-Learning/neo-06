package com.neobank.module.service;

/**
 * UC 03's AC4: the orchestrator could not be reached (or returned an error) while proxying
 * {@code GET /cases/{id}/applicant}. Mapped to {@code 503} — retryable, distinct from a {@code 404}
 * (this module's own case is fine; it is the upstream lookup that failed).
 */
public class OrchestratorUnavailableException extends RuntimeException {

    public OrchestratorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
