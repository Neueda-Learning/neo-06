package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UC03 — View Applicant. No Spring: the orchestrator client is mocked so this pins the doc's
 * acceptance criteria (AC1/AC4) without a running sidecar.
 */
class ApplicantServiceTest {

    private OrchestratorClient orchestrator;
    private ApplicantService service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(OrchestratorClient.class);
        service = new ApplicantService(orchestrator);
    }

    @Test
    void mariasApplicationMapsToTheSidebarsExactSubset() {
        // The doc's checkpoint (AC2): app-1234 -> "Maria Nowak · maria.nowak@example.com ·
        // CREDIT_CARD_REWARDS · terms accepted".
        Application application = new Application(
                "app-1234", "WEB", "2026-07-21T09:00:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", "maria.nowak@example.com",
                        "+48123456789", "PL", "PL", java.util.List.of("PL"), "RENTING", null, 24, 0),
                null, null, null,
                new Application.Product("CREDIT_CARD_REWARDS", 2800),
                null,
                new Application.Consents(true, true, false));
        when(orchestrator.getApplication("app-1234")).thenReturn(application);

        var applicant = service.getApplicant("app-1234");

        assertThat(applicant.fullName()).isEqualTo("Maria Nowak");
        assertThat(applicant.email()).isEqualTo("maria.nowak@example.com");
        assertThat(applicant.mobile()).isEqualTo("+48123456789");
        assertThat(applicant.productCode()).isEqualTo("CREDIT_CARD_REWARDS");
        assertThat(applicant.termsAccepted()).isTrue();
    }

    @Test
    void orchestratorUnreachableIsReportedAsRetryableNotA500() {
        when(orchestrator.getApplication("app-9999"))
                .thenThrow(new OrchestratorClient.OrchestratorFetchException("timed out", null));

        assertThatThrownBy(() -> service.getApplicant("app-9999"))
                .isInstanceOf(OrchestratorUnavailableException.class);
    }
}
