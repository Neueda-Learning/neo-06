package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.service.CaseService;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC02's HTTP surface: {@code GET /cases/{applicationId}} — the 200 shape, and the 404 that
 * {@link GlobalExceptionHandler} turns a {@link NoSuchElementException} into (AC7).
 */
@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

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
}
