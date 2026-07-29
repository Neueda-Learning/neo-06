package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.TimelineEntryView;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC 04 · {@code POST /cases/{id}/resend} — rotate a stale envelope and reset the expiry clock.
 * See {@code docs/uc-04-pending-expired-queue.md}'s AC3/AC4/AC5.
 *
 * <ul>
 *   <li>{@code PENDING} → rotate envelope, reset {@code expiresAt}, append a {@code RESENT} row,
 *       status unchanged. No callback — the orchestrator was already told {@code ACCEPTED} once.
 *       {@link com.neobank.module.model.AgreementRecord}'s stored PDF blob (UC05) is never
 *       touched.</li>
 *   <li>{@code EXPIRED} → same envelope/clock reset, status back to {@code PENDING}, history reads
 *       {@code EXPIRED → PENDING}, AND callback 1 ({@code Decision.ACCEPTED}) re-fires — the
 *       journey had timed out on the orchestrator's side too.</li>
 *   <li>{@code SIGNED}/{@code DECLINED}/{@code GENERATING} → {@code 409}
 *       ({@link ResendNotAllowedException}): terminal, or never sent in the first place.</li>
 * </ul>
 *
 * <p>The envelope rotation, the {@link AgreementRecord} update and the
 * {@link AgreementStatusHistory} append all happen in the one {@code @Transactional} method, per
 * the brief's own constraint.</p>
 */
@Service
public class ResendService {

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;
    private final OrchestratorClient orchestrator;
    private final EnvelopeService envelopes;

    public ResendService(AgreementRecordRepository agreementRecords,
                         AgreementStatusHistoryRepository history,
                         OrchestratorClient orchestrator,
                         EnvelopeService envelopes) {
        this.agreementRecords = agreementRecords;
        this.history = history;
        this.orchestrator = orchestrator;
        this.envelopes = envelopes;
    }

    /**
     * @throws NoSuchElementException    no case for {@code applicationId} ({@code 404})
     * @throws ResendNotAllowedException the case is not {@code PENDING}/{@code EXPIRED} ({@code 409})
     */
    @Transactional
    public CaseDetailView resend(String applicationId, String operator) {
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));

        AgreementStatus fromStatus = record.getStatus();
        if (fromStatus != AgreementStatus.PENDING && fromStatus != AgreementStatus.EXPIRED) {
            throw new ResendNotAllowedException(
                    "case " + applicationId + " is " + fromStatus + " — nothing to resend");
        }

        EnvelopeService.Registration registration = envelopes.register(applicationId);
        record.sendForSignature(registration.envelopeId(), registration.sentAt(), registration.expiresAt());
        agreementRecords.save(record);

        String event = fromStatus == AgreementStatus.EXPIRED ? "EXPIRED_TO_PENDING_RESEND" : "RESENT";
        history.save(new AgreementStatusHistory(applicationId, fromStatus, AgreementStatus.PENDING,
                event, operator, registration.sentAt()));

        if (fromStatus == AgreementStatus.EXPIRED) {
            // The journey had timed out on the orchestrator's side too — re-tell it ACCEPTED so
            // the case is back in play (AC4).
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "case resent after expiry — back in PENDING");
        }

        var timeline = history.findByApplicationIdOrderByOccurredAtAsc(applicationId).stream()
                .map(TimelineEntryView::of)
                .toList();
        return CaseDetailView.of(record, timeline);
    }
}
