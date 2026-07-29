package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * UC 04 · Pending/Expired Queue — see {@code docs/uc-04-pending-expired-queue.md} AC1/AC2. No
 * Spring, no database: both repositories are mocked.
 */
class QueueServiceTest {

    private AgreementRecordRepository agreementRecords;
    private AgreementStatusHistoryRepository history;
    private QueueService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        history = mock(AgreementStatusHistoryRepository.class);
        service = new QueueService(agreementRecords, history);
    }

    @Test
    void pendingStateReturnsOnlyPendingRowsOldestFirstWithEnvelopeCountAndAgeHours() {
        Instant sentAt = Instant.now().minusSeconds(3 * 3600);
        Instant expiresAt = sentAt.plusSeconds(5 * 24 * 3600);
        AgreementRecord tom = new AgreementRecord("app-tom", AgreementStatus.PENDING, null,
                "env-1", null, null, null, null, sentAt, expiresAt, null);
        when(agreementRecords.findByStatusOrderBySentAtAsc(eq(AgreementStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(tom));
        when(history.countByApplicationIdAndToStatus("app-tom", AgreementStatus.PENDING)).thenReturn(2L);

        var queue = service.findQueue(AgreementStatus.PENDING, 10);

        assertThat(queue).singleElement().satisfies(entry -> {
            assertThat(entry.applicationId()).isEqualTo("app-tom");
            assertThat(entry.state()).isEqualTo("PENDING");
            assertThat(entry.sentAt()).isEqualTo(sentAt);
            assertThat(entry.expiresAt()).isEqualTo(expiresAt);
            assertThat(entry.envelopeCount()).isEqualTo(2L);
            assertThat(entry.ageHours()).isEqualTo(3L);
        });
    }

    @Test
    void expiredStateQueriesTheExpiredStatusNotPending() {
        when(agreementRecords.findByStatusOrderBySentAtAsc(eq(AgreementStatus.EXPIRED), any(Pageable.class)))
                .thenReturn(List.of());

        var queue = service.findQueue(AgreementStatus.EXPIRED, 10);

        assertThat(queue).isEmpty();
    }

    @Test
    void anEmptyQueueIsJustAnEmptyListNotAnError() {
        when(agreementRecords.findByStatusOrderBySentAtAsc(eq(AgreementStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.findQueue(AgreementStatus.PENDING, 10)).isEmpty();
    }

    @Test
    void theLimitIsCappedAtTenRegardlessOfWhatIsAsked() {
        when(agreementRecords.findByStatusOrderBySentAtAsc(eq(AgreementStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        service.findQueue(AgreementStatus.PENDING, 500);

        verify(agreementRecords).findByStatusOrderBySentAtAsc(AgreementStatus.PENDING, PageRequest.of(0, 10));
    }
}
