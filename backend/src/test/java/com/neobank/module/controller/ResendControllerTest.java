package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.service.ResendNotAllowedException;
import com.neobank.module.service.ResendService;
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
 * UC 04's HTTP surface: {@code POST /cases/{id}/resend} — see
 * {@code docs/uc-04-pending-expired-queue.md} AC3/AC4/AC5.
 */
@WebMvcTest(ResendController.class)
class ResendControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ResendService resend;

    private static final String BODY = """
            {"operator":"operator-1"}
            """;

    @Test
    void aSuccessfulResendReturnsTheUpdatedCase() throws Exception {
        CaseDetailView detail = new CaseDetailView("PENDING", null, null, null, null, null,
                "env-new", Instant.now(), Instant.now(), null, List.of());
        when(resend.resend(eq("app-1"), eq("operator-1"))).thenReturn(detail);

        mvc.perform(post("/cases/app-1/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.envelopeId").value("env-new"));
    }

    @Test
    void anUnknownCaseIs404() throws Exception {
        when(resend.resend(eq("ghost"), any())).thenThrow(new NoSuchElementException("no case ghost"));

        mvc.perform(post("/cases/ghost/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no case ghost"));
    }

    @Test
    void aSignedCaseIs409() throws Exception {
        when(resend.resend(eq("app-2"), any()))
                .thenThrow(new ResendNotAllowedException("case app-2 is SIGNED — nothing to resend"));

        mvc.perform(post("/cases/app-2/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("case app-2 is SIGNED — nothing to resend"));
    }

    @Test
    void aMissingOperatorIs400BeforeReachingTheService() throws Exception {
        mvc.perform(post("/cases/app-3/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resend);
    }
}
