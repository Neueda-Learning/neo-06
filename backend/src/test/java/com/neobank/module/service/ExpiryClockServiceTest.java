package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UC 07's AC5 needs a real clock ticking — see the class's own javadoc for why this exists
 * ahead of UC 04's own build. No Spring: {@link ExpiryClockService#sweep} is called directly,
 * exactly as a real {@code @Scheduled} trigger would.
 */
class ExpiryClockServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private ExpiryClockService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        service = new ExpiryClockService(agreementRecords, history);
        when(agreementRecords.save(any(AgreementRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aPendingCasePastItsExpiresAtIsFlippedToExpiredWithAHistoryRow() {
        AgreementRecord row = new AgreementRecord("app-1", AgreementStatus.PENDING, "env-1");
        when(agreementRecords.findByStatusAndExpiresAtBefore(eq(AgreementStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(row));

        service.sweep();

        assertThat(row.getStatus()).isEqualTo(AgreementStatus.EXPIRED);
        verify(agreementRecords).save(row);

        ArgumentCaptor<AgreementStatusHistory> saved = ArgumentCaptor.forClass(AgreementStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(AgreementStatus.PENDING);
        assertThat(saved.getValue().getToStatus()).isEqualTo(AgreementStatus.EXPIRED);
        assertThat(saved.getValue().getEvent()).isEqualTo("EXPIRY_CLOCK");
        assertThat(saved.getValue().getActor()).isEqualTo("system");
    }

    @Test
    void noPastDueCasesMeansNothingHappens() {
        when(agreementRecords.findByStatusAndExpiresAtBefore(eq(AgreementStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        service.sweep();

        verify(agreementRecords, never()).save(any());
        verify(history, never()).save(any());
    }
}
