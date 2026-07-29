package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
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
 * UC04's queue read: {@code GET /queue?state=&limit=}.
 */
@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private QueueService queue;

    @Test
    void listsEntriesForTheRequestedState() throws Exception {
        QueueEntryView entry = new QueueEntryView("app-1", "PENDING",
                Instant.parse("2026-07-21T21:41:00Z"), Instant.parse("2026-07-26T21:41:00Z"), 1, 12);
        given(queue.list(eq(AgreementStatus.PENDING), any(Integer.class))).willReturn(List.of(entry));

        mvc.perform(get("/queue").param("state", "PENDING").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("app-1"))
                .andExpect(jsonPath("$[0].state").value("PENDING"))
                .andExpect(jsonPath("$[0].envelopeCount").value(1));
    }

    @Test
    void anEmptyQueueIsClearNotAnError() throws Exception {
        given(queue.list(eq(AgreementStatus.EXPIRED), any(Integer.class))).willReturn(List.of());

        mvc.perform(get("/queue").param("state", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anUnknownStateIsA400NotA500() throws Exception {
        mvc.perform(get("/queue").param("state", "BOGUS"))
                .andExpect(status().isBadRequest());
    }
}
