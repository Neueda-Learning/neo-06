package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC02 — Review Agreement: assemble a case's stored terms and its full timeline. Read-only; it
 * computes nothing (see the doc's Build notes: "Reading replays — it never re-decides").
 */
@Service
public class CaseService {

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;

    public CaseService(AgreementRecordRepository agreementRecords,
                       AgreementStatusHistoryRepository history) {
        this.agreementRecords = agreementRecords;
        this.history = history;
    }

    /**
     * @throws NoSuchElementException when no {@link AgreementRecord} exists for the id (AC7 —
     *         {@code GlobalExceptionHandler} turns this into a {@code 404}, never a {@code 500}).
     */
    @Transactional(readOnly = true)
    public CaseDetailView getCase(String applicationId) {
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));

        var timeline = history.findByApplicationIdOrderByOccurredAtAsc(applicationId).stream()
                .map(TimelineEntryView::of)
                .toList();

        return CaseDetailView.of(record, timeline);
    }
}
