package com.neobank.module.integrations.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The inbound half of the contract this module didn't need until UC 03: reading an
 * {@link Application} back from the orchestrator, rather than telling it what was decided.
 *
 * <p>{@link OrchestratorClient} is the way out (a {@code PUT} reporting a decision); this is the
 * way in (a {@code GET} for the applicant sidebar, and the same read UC 01's board hydration uses
 * for a name column). Everything in the {@code orchestrator} package is the wire; everything else
 * in the module is local.</p>
 */
@Component
public class OrchestratorApplicationClient {

    private final RestClient http;
    private final String applicationsUrl;

    public OrchestratorApplicationClient(RestClient http,
            @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    /**
     * {@code GET /api/v1/applications/{applicationId}} — the whole {@link Application}, same
     * shape the orchestrator sends this module on {@code /execute}.
     *
     * <p>Unlike {@link OrchestratorClient#applicationStatusUpdate}, a failure here is NOT
     * swallowed — this call has nothing already committed to protect, and the caller (
     * {@code CaseService}) is the one that decides how "unreachable" becomes a retryable sidebar
     * state (UC 03's AC4), not this client.</p>
     *
     * @throws RestClientException if the orchestrator cannot be reached or answers with an error
     */
    public Application getApplication(String applicationId) {
        return http.get()
                .uri(applicationsUrl + "/" + applicationId)
                .retrieve()
                .body(Application.class);
    }
}
