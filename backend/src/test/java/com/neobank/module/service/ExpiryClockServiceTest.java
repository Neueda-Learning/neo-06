package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * UC04's expiry clock: {@code PENDING} cases past their {@code expiresAt} move to
 * {@code EXPIRED}. No Spring, no scheduler — {@link ExpiryClockService#tick} is called directly.
 */
class ExpiryClockServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private OrchestratorClient orchestrator;
    private ExpiryClockService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new ExpiryClockService(agreementRecords, history, orchestrator);
    }

    @Test
    void pendingCasePastItsExpiryMovesToExpiredAndFiresApplicationManual() {
        AgreementRecord record = new AgreementRecord("app-1", AgreementStatus.PENDING, "agr-1",
                "env-1", "2026-06-01", 2800, java.math.BigDecimal.valueOf(24.9), 84,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-06T00:00:00Z"), null);
        when(agreementRecords.findByStatusOrderBySentAtAsc(AgreementStatus.PENDING))
                .thenReturn(List.of(record));

        service.tick();

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.EXPIRED);
        verify(agreementRecords).save(record);
        verify(history).save(any());
        verify(orchestrator).applicationStatusUpdate(eq("app-1"), eq(Decision.REFERRED), anyString());
    }

    @Test
    void pendingCaseStillWithinItsWindowIsUntouched() {
        AgreementRecord record = new AgreementRecord("app-2", AgreementStatus.PENDING, "agr-2",
                "env-2", "2026-06-01", 2800, java.math.BigDecimal.valueOf(24.9), 84,
                Instant.now(), Instant.now().plusSeconds(600), null);
        when(agreementRecords.findByStatusOrderBySentAtAsc(AgreementStatus.PENDING))
                .thenReturn(List.of(record));

        service.tick();

        assertThat(record.getStatus()).isEqualTo(AgreementStatus.PENDING);
        verify(agreementRecords, never()).save(any());
        verify(orchestrator, never()).applicationStatusUpdate(anyString(), any(), anyString());
    }
}
