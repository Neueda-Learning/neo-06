package com.neobank.module.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.esignmock.AutoOutcome;
import com.neobank.module.integrations.esignmock.EsignMode;
import com.neobank.module.integrations.esignmock.EsignProviderConfig;
import com.neobank.module.integrations.esignmock.EsignProviderConfigService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC 07's HTTP surface: {@code GET}/{@code PUT} {@code /esign/config}. Always {@code 200} — see
 * {@code docs/uc-07-operate-mock-control-panel.md}.
 */
@WebMvcTest(EsignConfigController.class)
class EsignConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EsignProviderConfigService config;

    @Test
    void getReturnsTheDocsOwnContractExampleShapeByDefault() throws Exception {
        given(config.get()).willReturn(EsignProviderConfig.defaults());

        mvc.perform(get("/esign/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("INSTANT"))
                .andExpect(jsonPath("$.delaySeconds").value(0))
                .andExpect(jsonPath("$.autoOutcome").value("SIGN"))
                .andExpect(jsonPath("$.demoExpirySeconds").value(Matchers.nullValue()));
    }

    @Test
    void putWithOnlyModeAndDemoExpirySecondsFillsInDefaultsForTheRest() throws Exception {
        // The doc's own PUT example: {"mode":"SILENT","demoExpirySeconds":30} — no delaySeconds,
        // no autoOutcome, since SILENT needs neither.
        given(config.apply(any())).willAnswer(call -> call.getArgument(0));

        mvc.perform(put("/esign/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SILENT\",\"demoExpirySeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SILENT"))
                .andExpect(jsonPath("$.delaySeconds").value(0))
                .andExpect(jsonPath("$.autoOutcome").value("SIGN"))
                .andExpect(jsonPath("$.demoExpirySeconds").value(30));

        ArgumentCaptor<EsignProviderConfig> applied = ArgumentCaptor.forClass(EsignProviderConfig.class);
        verify(config).apply(applied.capture());
        assertThat(applied.getValue().mode()).isEqualTo(EsignMode.SILENT);
        assertThat(applied.getValue().delaySeconds()).isZero();
        assertThat(applied.getValue().autoOutcome()).isEqualTo(AutoOutcome.SIGN);
        assertThat(applied.getValue().demoExpirySeconds()).isEqualTo(30);
    }

    @Test
    void putWithoutModeIsRejected() throws Exception {
        mvc.perform(put("/esign/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delaySeconds\":5}"))
                .andExpect(status().isBadRequest());
    }
}
