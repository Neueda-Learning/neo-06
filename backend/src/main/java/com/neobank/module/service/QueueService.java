package com.neobank.module.service;

import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementConfig;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementConfigRepository;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 04 · Pending &amp; Expired Queue — the two filtered reads, plus the resend write.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-04-pending-expired-queue.md}. The document
 * is never regenerated on a resend — same blob, same terms (AC3/AC7): only the envelope and clock
 * move.</p>
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementConfigRepository agreementConfigs;
    private final AgreementStatusHistoryRepository history;
    private final OfferDocumentRepository offerDocuments;
    private final ApplicantService applicants;
    private final EsignProvider esignProvider;
    private final OrchestratorClient orchestrator;

    public QueueService(AgreementRecordRepository agreementRecords,
                        AgreementConfigRepository agreementConfigs,
                        AgreementStatusHistoryRepository history,
                        OfferDocumentRepository offerDocuments,
                        ApplicantService applicants,
                        EsignProvider esignProvider,
                        OrchestratorClient orchestrator) {
        this.agreementRecords = agreementRecords;
        this.agreementConfigs = agreementConfigs;
        this.history = history;
        this.offerDocuments = offerDocuments;
        this.applicants = applicants;
        this.esignProvider = esignProvider;
        this.orchestrator = orchestrator;
    }

    /** AC1/AC2: only cases in {@code state}, oldest {@code sentAt} first, capped at {@code limit}. */
    @Transactional(readOnly = true)
    public List<QueueEntryView> list(AgreementStatus state, int limit) {
        Instant now = Instant.now();
        return agreementRecords.findByStatusOrderBySentAtAsc(state).stream()
                .limit(limit)
                .map(record -> QueueEntryView.of(record,
                        history.countByApplicationIdAndToStatus(record.getApplicationId(),
                                AgreementStatus.PENDING),
                        now))
                .toList();
    }

    /**
     * AC3/AC4: from {@code PENDING}, rotates the envelope and resets the clock without changing
     * status; from {@code EXPIRED}, also revives the case to {@code PENDING} and re-fires
     * callback 1. Any other source status is a {@code 409} (AC5) — there is nothing to nudge.
     */
    @Transactional
    public QueueEntryView resend(String applicationId, String operator) {
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));
        AgreementStatus from = record.getStatus();
        if (from != AgreementStatus.PENDING && from != AgreementStatus.EXPIRED) {
            throw new CaseConflictException(
                    "cannot resend " + applicationId + " from " + from + " — nothing to nudge");
        }

        EnvelopeRegistration registration =
                esignProvider.registerEnvelope(applicationId, documentShaFor(applicationId),
                        signerNameFor(applicationId));
        Instant sentAt = Instant.now();
        AgreementConfig config = agreementConfigs.findTopByOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("no AgreementConfig seeded"));
        Instant expiresAt = sentAt.plusSeconds((long) config.getExpiryDays() * 24 * 3600);
        record.markSentForSignature(registration.envelopeId(), sentAt, expiresAt);
        agreementRecords.save(record);

        String event = from == AgreementStatus.EXPIRED ? "RESEND" : "RESENT";
        history.save(new AgreementStatusHistory(applicationId, from, AgreementStatus.PENDING,
                event, operator, sentAt));

        if (from == AgreementStatus.EXPIRED) {
            // AC4: reviving from EXPIRED re-fires callback 1 so the parked journey knows the
            // offer is live again.
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "resent by " + operator + " — case revived to PENDING");
        }
        log.info("RESENT {} by {} — envelope {} -> {}", applicationId, operator,
                record.getEnvelopeId(), registration.envelopeId());

        return QueueEntryView.of(record,
                history.countByApplicationIdAndToStatus(applicationId, AgreementStatus.PENDING),
                sentAt);
    }

    private String documentShaFor(String applicationId) {
        return offerDocuments.findByApplicationId(applicationId)
                .map(doc -> doc.getSha256())
                .orElse(null);
    }

    /** Best-effort — a resend must still succeed even if the orchestrator is unreachable. */
    private String signerNameFor(String applicationId) {
        try {
            return applicants.getApplicant(applicationId).fullName();
        } catch (OrchestratorUnavailableException e) {
            log.warn("could not fetch signer name for {} while resending: {}", applicationId,
                    e.getMessage());
            return null;
        }
    }
}
