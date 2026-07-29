package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OfferDocumentRepository;
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
    private final OfferDocumentRepository offerDocuments;

    public CaseService(AgreementRecordRepository agreementRecords,
                       AgreementStatusHistoryRepository history,
                       OfferDocumentRepository offerDocuments) {
        this.agreementRecords = agreementRecords;
        this.history = history;
        this.offerDocuments = offerDocuments;
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

        // Consent-gate DECLINED cases never got a document generated at all (UC00's decide()
        // bails out before generation) — the UI needs to know this so it can hide the
        // view/download actions instead of following a link that 409s.
        boolean documentAvailable = offerDocuments.findByApplicationId(applicationId).isPresent();

        return CaseDetailView.of(record, timeline, documentAvailable);
    }
}
