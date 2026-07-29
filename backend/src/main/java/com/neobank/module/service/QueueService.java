package com.neobank.module.service;

import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC 04 · Pending/Expired Queue — {@code GET /queue?state=PENDING|EXPIRED&limit=10}. Read-only:
 * two filtered reads over {@link com.neobank.module.model.AgreementRecord}, oldest {@code sentAt}
 * first, capped at 10 regardless of what {@code limit} asks for — see
 * {@code docs/uc-04-pending-expired-queue.md}'s build notes.
 *
 * <p>An empty result is not an error: no rows in the given state is "queue clear", and the
 * controller returns {@code 200} with an empty array either way — there is nothing extra to code
 * for that case.</p>
 */
@Service
public class QueueService {

    /** The queue is never more than this many rows, whatever a caller's {@code limit} asks for. */
    static final int MAX_LIMIT = 10;

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;

    public QueueService(AgreementRecordRepository agreementRecords,
                        AgreementStatusHistoryRepository history) {
        this.agreementRecords = agreementRecords;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<QueueEntryView> findQueue(AgreementStatus state, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return agreementRecords.findByStatusOrderBySentAtAsc(state, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(record -> QueueEntryView.of(record,
                        history.countByApplicationIdAndToStatus(record.getApplicationId(), AgreementStatus.PENDING)))
                .toList();
    }
}
