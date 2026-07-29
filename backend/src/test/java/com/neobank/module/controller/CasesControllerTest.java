package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCommand;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.service.CaseService;
import com.neobank.module.service.OverrideNotAllowedException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC02's HTTP surface: {@code GET /cases/{applicationId}} — the 200 shape, and the 404 that
 * {@link GlobalExceptionHandler} turns a {@link NoSuchElementException} into (AC7).
 *
 * <p>UC08's HTTP surface: {@code POST /cases/{applicationId}/override} — the override endpoint.</p>
 */
@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private CaseService cases;

    @Test
    void knownCaseReturnsTheFullContractShape() throws Exception {
        CaseDetailView detail = new CaseDetailView(
                "SIGNED", "agr-000123", "2026-06-01", 2800, new java.math.BigDecimal("24.9"), 84,
                "env-8f14e45f", Instant.parse("2026-07-21T21:41:00Z"),
                Instant.parse("2026-07-26T21:41:00Z"), Instant.parse("2026-07-25T10:03:00Z"),
                List.of(new TimelineEntryView("GENERATING", "PENDING", "ENVELOPE_SENT", "system",
                        Instant.parse("2026-07-21T21:41:00Z"))));
        given(cases.getCase(eq("app-1234"))).willReturn(detail);

        mvc.perform(get("/cases/app-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.reference").value("agr-000123"))
                .andExpect(jsonPath("$.termsVersion").value("2026-06-01"))
                .andExpect(jsonPath("$.approvedLimit").value(2800))
                .andExpect(jsonPath("$.minPaymentGbp").value(84))
                .andExpect(jsonPath("$.timeline", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline[0].event").value("ENVELOPE_SENT"));
    }

    @Test
    void unknownApplicationIdIsA404NotA500() throws Exception {
        given(cases.getCase(eq("ghost"))).willThrow(new NoSuchElementException("no case ghost"));

        mvc.perform(get("/cases/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ghost")));
    }

    // ========== UC 08 — Override Case tests ==========

    @Test
    void overrideReturns200WithUpdatedCase() throws Exception {
        CaseDetailView detail = new CaseDetailView(
                "DECLINED", "agr-001", "2026-06-01", 2800, new java.math.BigDecimal("24.9"), 84,
                "env-123", Instant.now(), Instant.now().plusSeconds(5 * 24 * 3600), null,
                List.of());
        given(cases.override(eq("app-1"), any(OverrideCommand.class))).willReturn(detail);

        OverrideCommand cmd = new OverrideCommand("DECLINED", "Customer confirmed by phone — not proceeding", "b.dimovski");

        mvc.perform(post("/cases/app-1/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void overrideSignedCaseReturns409() throws Exception {
        OverrideCommand cmd = new OverrideCommand("DECLINED", "reason", "operator");
        given(cases.override(eq("app-signed"), any(OverrideCommand.class)))
                .willThrow(new OverrideNotAllowedException("SIGNED is never overridden — the contract is in force"));

        mvc.perform(post("/cases/app-signed/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cmd)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SIGNED is never overridden — the contract is in force"));
    }

    @Test
    void overrideUnknownCaseReturns404() throws Exception {
        OverrideCommand cmd = new OverrideCommand("DECLINED", "reason", "operator");
        given(cases.override(eq("ghost"), any(OverrideCommand.class)))
                .willThrow(new NoSuchElementException("no case ghost"));

        mvc.perform(post("/cases/ghost/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cmd)))
                .andExpect(status().isNotFound());
    }
}
