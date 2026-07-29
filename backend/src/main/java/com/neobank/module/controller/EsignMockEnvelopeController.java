package com.neobank.module.controller;

import com.neobank.module.dto.EnvelopeRegistrationRequest;
import com.neobank.module.dto.EnvelopeRegistrationResponse;
import com.neobank.module.service.EnvelopeRegistration;
import com.neobank.module.service.EsignProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 07's OWN API — {@code POST /envelopes} in the brief, exposed here as
 * {@code POST /mock/esign/envelopes} so it cannot be confused with this module's real contract
 * surfaces ({@code /api/v1/applications}, {@code /cases/...}) while it lives in-process. See
 * {@code EsignMockService}'s class Javadoc for why there is no separate container.
 *
 * <p>{@link com.neobank.module.service.ApplicationService} does not call this endpoint over HTTP —
 * it calls {@link EsignProvider} directly, in-process. This controller exists so the mock's own
 * surface is still real, callable, and documented (OpenAPI) on its own, exactly as it would be if
 * it were split out into its own service tomorrow.</p>
 */
@RestController
public class EsignMockEnvelopeController {

    private final EsignProvider esignProvider;

    public EsignMockEnvelopeController(EsignProvider esignProvider) {
        this.esignProvider = esignProvider;
    }

    @PostMapping("/mock/esign/envelopes")
    public EnvelopeRegistrationResponse register(@RequestBody EnvelopeRegistrationRequest request) {
        EnvelopeRegistration registration = esignProvider.registerEnvelope(
                request.applicationId(), request.documentSha(), request.signerName());
        return new EnvelopeRegistrationResponse(registration.envelopeId());
    }
}
