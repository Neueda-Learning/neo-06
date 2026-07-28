package com.neobank.module.integrations.applicantlookup;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves an applicant name to application ids via the orchestrator — the "name search" half
 * of UC 01. The module never stores applicant data, so the only way to find a case by name is
 * to ask the system that holds the application which ids match, then read this module's own
 * rows for those ids.
 *
 * <p><b>Failures are logged, never thrown.</b> Per UC 01 AC7, an orchestrator that is down
 * must not turn a search into a {@code 500} — the id-based search path still works, and the
 * UI shows a retryable "—" for names it cannot fetch. An empty id list here means "no name
 * matches (or I could not ask)", and the service treats both the same way: no rows.</p>
 *
 * <p>The base URL is the same {@code service.orchestrator-url} the UC 00 callback uses — the
 * sidecar ({@code http://localhost:9000}) locally, the real orchestrator
 * ({@code http://orchestrator:8080}) in the system stack. Nothing else changes between them,
 * which is the rule the {@code orchestrator} package pins: one URL, both directions.</p>
 */
@Component
public class ApplicantLookupClient {

    private static final Logger log = LoggerFactory.getLogger(ApplicantLookupClient.class);

    private final RestClient http;
    private final String applicationsUrl;

    public ApplicantLookupClient(RestClient http,
                                 @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    /**
     * Resolve a name to the application ids the orchestrator holds for it. Never throws.
     *
     * @param name the applicant name fragment to search for (case-insensitive on the orchestrator side)
     * @return the matching ids, or an empty list if the orchestrator is unreachable or found nothing
     */
    public List<String> findApplicationIdsByName(String name) {
        URI uri = UriComponentsBuilder.fromHttpUrl(applicationsUrl)
                .queryParam("name", name)
                .build(false)
                .toUri();
        try {
            String[] ids = http.get()
                    .uri(uri)
                    .retrieve()
                    .body(String[].class);
            return ids == null ? List.of() : List.of(ids);
        } catch (Exception e) {
            log.warn("Name lookup to the orchestrator failed for '{}': {} — returning no ids. "
                    + "Id-based search still works; the UI will show '—' for names it cannot fetch.",
                    name, e.toString());
            return List.of();
        }
    }
}
