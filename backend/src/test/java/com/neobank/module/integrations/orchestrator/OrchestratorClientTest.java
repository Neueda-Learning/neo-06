package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.neobank.module.model.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * What this module actually PUTs to the orchestrator.
 *
 * <p><b>Why at the socket and not with Mockito.</b> Every other test of the outbound half asserts
 * {@code verify(orchestrator).applicationStatusUpdate(eq(id), eq(decision), any())} — a check on
 * Java arguments, which passes whether or not anything ever reaches the wire. A dropped field, a
 * {@code PUT} that became a {@code POST}, a URL that lost {@code /api/v1/applications}, or a status
 * arriving lower-cased are all green under that. The inbound {@code 202} is pinned four fields
 * deep in this repo; the half carrying this module's own decision was the untested one.</p>
 *
 * <p>That matters most for {@code PENDING}, which is new and is the one status whose meaning is
 * "do not advance". If it reached the orchestrator misspelled, the journey would not hold — it
 * would sit unrecognised and be failed by the sweeper thirty seconds later, and the customer would
 * never be offered the agreement.</p>
 */
class OrchestratorClientTest {

    private static final String ORCHESTRATOR = "http://orchestrator:8080";

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(builder.build(), "neo06", ORCHESTRATOR);
    }

    private void expectStatusUpdate(String applicationId, String status, String comment) {
        server.expect(once(), requestTo(ORCHESTRATOR + "/api/v1/applications/" + applicationId))
                .andExpect(method(PUT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.serviceId").value("neo06"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.comment").value(comment))
                // The id identifies the resource and rides in the URL. Sending it in the body
                // as well is only a way for the two to disagree.
                .andExpect(jsonPath("$.applicationId").doesNotExist())
                .andRespond(withSuccess("{\"received\":true}", MediaType.APPLICATION_JSON));
    }

    @Test
    void pendingGoesOverTheWireAsTheExactWordTheOrchestratorHoldsOn() {
        expectStatusUpdate("SIM-01", "PENDING", "agreement sent for signature");

        client.applicationStatusUpdate("SIM-01", Decision.PENDING, "agreement sent for signature");

        server.verify();
    }

    @Test
    void theThreeRealOutcomesGoOverTheWireUnchangedToo() {
        expectStatusUpdate("SIM-02", "ACCEPTED", "signed");
        expectStatusUpdate("SIM-03", "REJECTED", "declined");
        expectStatusUpdate("SIM-04", "REFERRED", "expired");

        client.applicationStatusUpdate("SIM-02", Decision.ACCEPTED, "signed");
        client.applicationStatusUpdate("SIM-03", Decision.REJECTED, "declined");
        client.applicationStatusUpdate("SIM-04", Decision.REFERRED, "expired");

        server.verify();
    }

    /**
     * The decision is already committed to our own database by the time this runs, so a failure
     * here rolls nothing back and re-throwing would only kill the worker thread. The orchestrator
     * treats a missing report as a timeout, which is its job.
     */
    @Test
    void anOrchestratorThatIsDownIsLoggedRatherThanThrown() {
        server.expect(once(), requestTo(ORCHESTRATOR + "/api/v1/applications/SIM-05"))
                .andRespond(withServerError());

        assertThatCode(() ->
                client.applicationStatusUpdate("SIM-05", Decision.PENDING, "sent"))
                .doesNotThrowAnyException();

        server.verify();
    }
}
