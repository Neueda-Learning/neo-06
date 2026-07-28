package com.neobank.module.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.AgreementDocumentContent;
import com.neobank.module.service.AgreementDocumentService;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the wire for {@code GET /cases/{id}/document} — the minimal first slice of UC05 (see
 * {@code prompts/uc-05-prompt.md}). {@link AgreementDocumentService} is mocked here; its own
 * hardcoded content is exercised in {@link com.neobank.module.service.AgreementDocumentServiceTest}.
 */
@WebMvcTest(AgreementDocumentController.class)
class AgreementDocumentControllerTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AgreementDocumentService documents;

    @Test
    void servesTheHardcodedDocumentInline() throws Exception {
        Mockito.when(documents.getDocument("app-1234"))
                .thenReturn(new AgreementDocumentContent(BYTES, "application/pdf", "agreement-app-1234.pdf"));

        mvc.perform(get("/cases/app-1234/document"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void addsAttachmentHeaderOnDownload() throws Exception {
        Mockito.when(documents.getDocument("app-1234"))
                .thenReturn(new AgreementDocumentContent(BYTES, "application/pdf", "agreement-app-1234.pdf"));

        mvc.perform(get("/cases/app-1234/document").param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"agreement-app-1234.pdf\""));
    }

    @Test
    void unknownApplicationIdIs404() throws Exception {
        Mockito.when(documents.getDocument("unknown"))
                .thenThrow(new NoSuchElementException("no agreement case for applicationId unknown"));

        mvc.perform(get("/cases/unknown/document"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no agreement case for applicationId unknown"));
    }
}
