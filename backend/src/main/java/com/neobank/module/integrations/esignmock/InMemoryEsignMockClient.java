package com.neobank.module.integrations.esignmock;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The stand-in for UC 07's real mock control panel — see the package javadoc.
 *
 * <p>No network call, no persistence of its own: it hands back a random-looking envelope id
 * synchronously, which is all {@link com.neobank.module.service.EnvelopeService}'s callers need
 * (the id itself is opaque to this module — {@code EsignProviderConfig}'s {@code mode}/
 * {@code autoOutcome} dials, and actually posting a signature event back, are UC 07/UC 06's job,
 * not this client's).</p>
 */
@Component
public class InMemoryEsignMockClient implements EsignMockClient {

    @Override
    public String registerEnvelope(String applicationId) {
        return "env-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
