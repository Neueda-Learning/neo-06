package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.CaseSummaryView;
import com.neobank.module.dto.OverrideRequest;
import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.dto.ResendRequest;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseConflictException;
import com.neobank.module.service.CaseSearchService;
import com.neobank.module.service.CaseService;
import com.neobank.module.service.OverrideService;
import com.neobank.module.service.QueueService;
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
 */
@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseService cases;

    @MockBean
    private CaseSearchService search;

    @MockBean
    private ApplicantService applicants;

    @MockBean
    private QueueService queue;

    @MockBean
    private OverrideService overrides;

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

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void searchDelegatesToCaseSearchService() throws Exception {
        CaseSearchResult result = new CaseSearchResult(
                List.of(new CaseSummaryView("app-1234", "SIGNED", "2026-06-01",
                        Instant.parse("2026-07-21T21:41:00Z"), Instant.parse("2026-07-25T10:03:00Z"))),
                false);
        given(search.search(eq("Maria"), org.mockito.ArgumentMatchers.isNull(), eq(10)))
                .willReturn(result);

        mvc.perform(get("/cases").param("q", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].applicationId").value("app-1234"))
                .andExpect(jsonPath("$.items[0].status").value("SIGNED"))
                .andExpect(jsonPath("$.more").value(false));
    }

    @Test
    void getApplicantDelegatesToApplicantService() throws Exception {
        given(applicants.getApplicant(eq("app-1234"))).willReturn(new ApplicantView(
                "Maria Nowak", "maria.nowak@example.com", "+48123456789", "CREDIT_CARD_REWARDS",
                true));

        mvc.perform(get("/cases/app-1234/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Nowak"))
                .andExpect(jsonPath("$.email").value("maria.nowak@example.com"))
                .andExpect(jsonPath("$.productCode").value("CREDIT_CARD_REWARDS"))
                .andExpect(jsonPath("$.termsAccepted").value(true));
    }

    @Test
    void resendDelegatesToQueueService() throws Exception {
        QueueEntryView entry = new QueueEntryView("app-1234", "PENDING",
                Instant.parse("2026-07-21T21:41:00Z"), Instant.parse("2026-07-26T21:41:00Z"), 2, 0);
        given(queue.resend(eq("app-1234"), eq("op-1"))).willReturn(entry);

        mvc.perform(post("/cases/app-1234/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ResendRequest("op-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.envelopeCount").value(2));
    }

    @Test
    void resendOnASignedCaseIsA409() throws Exception {
        given(queue.resend(eq("app-1234"), eq("op-1")))
                .willThrow(new CaseConflictException("cannot resend app-1234 from SIGNED"));

        mvc.perform(post("/cases/app-1234/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ResendRequest("op-1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void overrideDelegatesToOverrideService() throws Exception {
        QueueEntryView entry = new QueueEntryView("app-1234", "DECLINED", null, null, 1, 0);
        given(overrides.override(eq("app-1234"), any(OverrideRequest.class))).willReturn(entry);

        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new OverrideRequest("DECLINED", "customer withdrew", "op-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DECLINED"));
    }

    @Test
    void overrideMissingReasonIsA400() throws Exception {
        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new OverrideRequest("DECLINED", "", "op-1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideOnASignedCaseIsA409WithTheExactMessage() throws Exception {
        given(overrides.override(eq("app-1234"), any(OverrideRequest.class)))
                .willThrow(new CaseConflictException("no override may unsign a contract"));

        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new OverrideRequest("DECLINED", "customer withdrew", "op-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("no override may unsign a contract"));
    }
}
