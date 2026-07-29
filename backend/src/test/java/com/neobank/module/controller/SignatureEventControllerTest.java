package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.dto.SignatureEventResponse;
import com.neobank.module.service.SignatureEventConflictException;
import com.neobank.module.service.SignatureEventService;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the wire for {@code POST /cases/{id}/signature-events}: the JSON shape, the status codes
 * {@link GlobalExceptionHandler} turns the service's exceptions into, and — the one rule this
 * endpoint enforces at the door rather than in the service — that {@code event} outside
 * {@code SIGNED}/{@code DECLINED} never reaches {@link SignatureEventService} at all (AC 8).
 */
@WebMvcTest(SignatureEventController.class)
class SignatureEventControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SignatureEventService signatureEvents;

    private static String body(String event) {
        return """
                {"envelopeId":"env-8f14e45f","event":"%s","occurredAt":"2026-07-25T10:03:00Z"}
                """.formatted(event);
    }

    @Test
    void signedIsAcceptedAndForwardedToTheService() throws Exception {
        when(signatureEvents.apply(eq("APP-1"), any(SignatureEventRequest.class)))
                .thenReturn(SignatureEventResponse.applied("APP-1", com.neobank.module.model.AgreementStatus.SIGNED));

        mvc.perform(post("/cases/APP-1/signature-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SIGNED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("APP-1"))
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.replay").value(false));

        ArgumentCaptor<SignatureEventRequest> sent = ArgumentCaptor.forClass(SignatureEventRequest.class);
        verify(signatureEvents).apply(eq("APP-1"), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().envelopeId()).isEqualTo("env-8f14e45f");
    }

    @Test
    void anEventOutsideSignedOrDeclinedIs400BeforeReachingTheService() throws Exception {
        // AC 8: EXPIRED belongs to this module's own clock, never to a caller.
        mvc.perform(post("/cases/APP-2/signature-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXPIRED")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(signatureEvents);
    }

    @Test
    void anUnknownCaseIs404() throws Exception {
        when(signatureEvents.apply(eq("GHOST"), any(SignatureEventRequest.class)))
                .thenThrow(new NoSuchElementException("no such case: GHOST"));

        mvc.perform(post("/cases/GHOST/signature-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SIGNED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no such case: GHOST"));
    }

    @Test
    void aConflictFromTheServiceIs409() throws Exception {
        when(signatureEvents.apply(eq("APP-3"), any(SignatureEventRequest.class)))
                .thenThrow(new SignatureEventConflictException("case APP-3 is GENERATING, not PENDING"));

        mvc.perform(post("/cases/APP-3/signature-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SIGNED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("case APP-3 is GENERATING, not PENDING"));
    }

    @Test
    void aMissingEnvelopeIdIs400() throws Exception {
        mvc.perform(post("/cases/APP-4/signature-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"SIGNED","occurredAt":"2026-07-25T10:03:00Z"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(signatureEvents);
    }
}
