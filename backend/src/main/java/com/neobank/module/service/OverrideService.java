package com.neobank.module.service;

import com.neobank.module.dto.OverrideRequest;
import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementConfig;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.repository.AgreementConfigRepository;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import com.neobank.module.repository.OverrideLogRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 08 · Override Case — the operator's manual escape hatch.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-08-override-case.md}. Legal moves only
 * (build note): {@code PENDING -> DECLINED} (stop a live offer), {@code EXPIRED -> DECLINED}
 * (abandon), {@code DECLINED -> PENDING} (revive — same envelope/clock reset as a resend).
 * {@code SIGNED} is never a target and never a source (AC3, exact wording checkpoint). The blob
 * and history are never edited, only appended to (AC7).</p>
 */
@Service
public class OverrideService {

    private static final Logger log = LoggerFactory.getLogger(OverrideService.class);

    /** AC3's exact wording — the checkpoint test asserts this literal string. */
    private static final String SIGNED_MESSAGE = "no override may unsign a contract";

    private static final Set<AgreementStatus> LEGAL_TARGETS =
            Set.of(AgreementStatus.PENDING, AgreementStatus.DECLINED);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementConfigRepository agreementConfigs;
    private final AgreementStatusHistoryRepository history;
    private final OverrideLogRepository overrideLogs;
    private final OfferDocumentRepository offerDocuments;
    private final ApplicantService applicants;
    private final EsignProvider esignProvider;
    private final OrchestratorClient orchestrator;
    private final AgreementDocumentComposer documents;

    public OverrideService(AgreementRecordRepository agreementRecords,
                           AgreementConfigRepository agreementConfigs,
                           AgreementStatusHistoryRepository history,
                           OverrideLogRepository overrideLogs,
                           OfferDocumentRepository offerDocuments,
                           ApplicantService applicants,
                           EsignProvider esignProvider,
                           OrchestratorClient orchestrator,
                           AgreementDocumentComposer documents) {
        this.agreementRecords = agreementRecords;
        this.agreementConfigs = agreementConfigs;
        this.history = history;
        this.overrideLogs = overrideLogs;
        this.offerDocuments = offerDocuments;
        this.applicants = applicants;
        this.esignProvider = esignProvider;
        this.orchestrator = orchestrator;
        this.documents = documents;
    }

    @Transactional
    public QueueEntryView override(String applicationId, OverrideRequest request) {
        AgreementStatus newStatus = parseTarget(request.newStatus());

        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no case " + applicationId));
        AgreementStatus oldStatus = record.getStatus();

        if (oldStatus == AgreementStatus.SIGNED) {
            throw new CaseConflictException(SIGNED_MESSAGE);
        }
        if (!isLegalMove(oldStatus, newStatus)) {
            throw new CaseConflictException(
                    "cannot override " + applicationId + " from " + oldStatus + " to " + newStatus);
        }

        Instant now = Instant.now();
        if (newStatus == AgreementStatus.PENDING) {
            AgreementConfig config = agreementConfigs.findTopByOrderByVersionDesc()
                    .orElseThrow(() -> new IllegalStateException("no AgreementConfig seeded"));
            // Revive: same mechanics as a resend (AC6) — fresh envelope, fresh clock. A
            // consent-gate DECLINED case never had terms pinned or a document generated at all
            // (UC00's decide() bails out before either step) — reviving it is the first time this
            // case gets one. AC7 still holds for every other case: an existing document is never
            // touched.
            ensureDocumentGenerated(applicationId, record, config);
            EnvelopeRegistration registration = esignProvider.registerEnvelope(applicationId,
                    documentShaFor(applicationId), signerNameFor(applicationId));
            Instant expiresAt = now.plusSeconds((long) config.getExpiryDays() * 24 * 3600);
            record.markSentForSignature(registration.envelopeId(), now, expiresAt);
        } else {
            record.changeStatus(newStatus);
        }
        agreementRecords.save(record);

        overrideLogs.save(new OverrideLog(applicationId, oldStatus, newStatus, request.reason(),
                request.operator(), now));
        history.save(new AgreementStatusHistory(applicationId, oldStatus, newStatus, "OVERRIDE",
                request.operator(), now));

        // AC5: a fresh callback carrying "local-manual" and the outcome word for the new state —
        // there is no Decision value for a human override, so it is encoded in the comment.
        Decision decision = newStatus == AgreementStatus.DECLINED ? Decision.REJECTED : Decision.ACCEPTED;
        String outcome = newStatus == AgreementStatus.DECLINED ? "declined" : "application-manual";
        orchestrator.applicationStatusUpdate(applicationId, decision,
                "local-manual: " + outcome + " (override by " + request.operator() + ")");

        log.info("OVERRIDE {} {} -> {} by {}", applicationId, oldStatus, newStatus,
                request.operator());

        return QueueEntryView.of(record,
                history.countByApplicationIdAndToStatus(applicationId, AgreementStatus.PENDING),
                now);
    }

    private static AgreementStatus parseTarget(String newStatus) {
        AgreementStatus parsed;
        try {
            parsed = AgreementStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("newStatus must be PENDING or DECLINED");
        }
        if (!LEGAL_TARGETS.contains(parsed)) {
            throw new IllegalArgumentException("newStatus must be PENDING or DECLINED");
        }
        return parsed;
    }

    private static boolean isLegalMove(AgreementStatus from, AgreementStatus to) {
        if (to == AgreementStatus.DECLINED) {
            return from == AgreementStatus.PENDING || from == AgreementStatus.EXPIRED;
        }
        // to == PENDING (revive)
        return from == AgreementStatus.DECLINED;
    }

    private String documentShaFor(String applicationId) {
        return offerDocuments.findByApplicationId(applicationId)
                .map(doc -> doc.getSha256())
                .orElse(null);
    }

    /**
     * Fills the gap left by a consent-gate DECLINED case: {@code decide()} bails out before
     * pinning terms or generating a document at all, so a plain revive would otherwise send a
     * case out for signature with nothing to sign. A document that already exists (any other
     * DECLINED case — one that was PENDING/EXPIRED before an operator declined it) is never
     * touched, preserving AC7's byte-identity guarantee.
     *
     * <p>The application's payload itself is never stored (platform rule) — this re-fetches it
     * from the orchestrator, exactly as {@code ApplicationService.decide()} would have priced it
     * the first time.</p>
     */
    private void ensureDocumentGenerated(String applicationId, AgreementRecord record,
            AgreementConfig config) {
        if (offerDocuments.findByApplicationId(applicationId).isPresent()) {
            return;
        }
        Application application;
        try {
            application = orchestrator.getApplication(applicationId);
        } catch (RuntimeException unreachable) {
            log.warn("could not fetch application {} while reviving — no document generated: {}",
                    applicationId, unreachable.getMessage());
            return;
        }
        if (application == null) {
            return;
        }
        Application.Product product = application.product();
        Application.Applicant applicant = application.applicant();
        Integer approvedLimit = product == null ? null : product.requestedCreditLimit();
        Integer minPaymentGbp = approvedLimit == null ? null : config.minPaymentFor(approvedLimit);

        if (record.getReference() == null) {
            record.pinTerms(referenceFor(applicationId), config.getTermsVersion(), approvedLimit,
                    config.getAprPercent(), minPaymentGbp);
        }
        documents.compose(applicationId, new AgreementDocumentComposer.Content(
                applicant == null ? null : applicant.fullName(),
                product == null ? null : product.productCode(),
                approvedLimit, config.getAprPercent(), minPaymentGbp, config.getTermsVersion()));
    }

    /** Same deterministic formula as {@code ApplicationService.reference} — a case pinned late by
     * a revive earns the same reference it would have gotten at generation. */
    private static String referenceFor(String applicationId) {
        int hash = Math.abs(applicationId.hashCode()) % 1_000_000;
        return String.format("agr-%06d", hash);
    }

    private String signerNameFor(String applicationId) {
        try {
            return applicants.getApplicant(applicationId).fullName();
        } catch (OrchestratorUnavailableException e) {
            log.warn("could not fetch signer name for {} while reviving: {}", applicationId,
                    e.getMessage());
            return null;
        }
    }
}
