package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.esignmock.EsignMockClient;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * {@link EnvelopeService} just adds the provider's expiry window to "now" — no Spring, one mocked
 * collaborator.
 */
class EnvelopeServiceTest {

    @Test
    void registerAddsTheProvidersExpiryWindowToNowAndPassesTheEnvelopeIdThrough() {
        EsignMockClient esignMock = mock(EsignMockClient.class);
        when(esignMock.registerEnvelope("app-1"))
                .thenReturn(new EsignMockClient.EnvelopeRegistration("env-abc12345", 30));

        EnvelopeService service = new EnvelopeService(esignMock);
        Instant before = Instant.now();

        EnvelopeService.Registration registration = service.register("app-1");

        assertThat(registration.envelopeId()).isEqualTo("env-abc12345");
        assertThat(registration.sentAt()).isBetween(before, Instant.now());
        assertThat(registration.expiresAt()).isEqualTo(registration.sentAt().plusSeconds(30));
        verify(esignMock).registerEnvelope("app-1");
    }
}
