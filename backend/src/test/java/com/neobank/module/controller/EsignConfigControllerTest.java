package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.EsignConfigUpdate;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.model.EsignMode;
import com.neobank.module.model.EsignOutcome;
import com.neobank.module.service.EsignProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC 07's admin surface: {@code GET}/{@code PUT /esign/config}.
 */
@WebMvcTest(EsignConfigController.class)
class EsignConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EsignProvider esignProvider;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void getReturnsTheCurrentDials() throws Exception {
        given(esignProvider.getConfig()).willReturn(
                new EsignConfigView(EsignMode.INSTANT, 0, EsignOutcome.SIGN, null));

        mvc.perform(get("/esign/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("INSTANT"))
                .andExpect(jsonPath("$.autoOutcome").value("SIGN"))
                .andExpect(jsonPath("$.demoExpirySeconds").doesNotExist());
    }

    @Test
    void putAppliesTheUpdateAndReturnsTheNewDials() throws Exception {
        given(esignProvider.updateConfig(any(EsignConfigUpdate.class))).willReturn(
                new EsignConfigView(EsignMode.SILENT, 5, EsignOutcome.SIGN, 30));

        mvc.perform(put("/esign/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new EsignConfigUpdate(EsignMode.SILENT, null, null, 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SILENT"))
                .andExpect(jsonPath("$.demoExpirySeconds").value(30));
    }
}
