package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.service.QueueService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC 04's HTTP surface: {@code GET /queue?state=PENDING|EXPIRED&limit=10} — see
 * {@code docs/uc-04-pending-expired-queue.md}.
 */
@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private QueueService queue;

    @Test
    void pendingStateReturnsTheContractShape() throws Exception {
        QueueEntryView entry = new QueueEntryView("app-tom", "PENDING",
                Instant.parse("2026-07-21T21:41:00Z"), Instant.parse("2026-07-26T21:41:00Z"), 1, 3);
        given(queue.findQueue(eq(AgreementStatus.PENDING), eq(10))).willReturn(List.of(entry));

        mvc.perform(get("/queue").param("state", "PENDING").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("app-tom"))
                .andExpect(jsonPath("$[0].state").value("PENDING"))
                .andExpect(jsonPath("$[0].envelopeCount").value(1))
                .andExpect(jsonPath("$[0].ageHours").value(3));
    }

    @Test
    void anEmptyQueueIs200WithAnEmptyArrayNotAnError() throws Exception {
        given(queue.findQueue(eq(AgreementStatus.EXPIRED), eq(10))).willReturn(List.of());

        mvc.perform(get("/queue").param("state", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anInvalidStateIs400BeforeReachingTheService() throws Exception {
        mvc.perform(get("/queue").param("state", "SIGNED_BUT_MADE_UP"))
                .andExpect(status().isBadRequest());
    }
}
