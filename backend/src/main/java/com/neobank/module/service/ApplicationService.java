package com.neobank.module.service;

import com.neobank.module.dto.AgreementRecordView;
import com.neobank.module.dto.EsignConfigView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementConfig;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementConfigRepository;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 00 · Process Application — the durable row, its idempotency, and the async hand-off.</h2>
 *
 * <p>Once the consent gate has passed: the case's terms are pinned from the current
 * {@link AgreementConfig} (see {@link #pinTerms}), UC05's agreement PDF is generated (via
 * {@link AgreementDocumentComposer}) with those terms printed on it, an envelope is registered
 * with UC 07's e-sign provider (via {@link EsignProvider}), the row moves
 * {@code GENERATING → PENDING} carrying that envelope and its expiry, and the orchestrator is told
 * {@code PENDING} so the journey <em>holds on this step</em> until the customer signs. What
 * belongs here is exactly what the brief's acceptance criteria ask for, plus that:</p>
 *
 * <ol>
 *   <li>the {@link AgreementRecord} row exists, committed, BEFORE the {@code 202} is sent — so a
 *       crash right after the ack loses nothing;</li>
 *   <li>only {@code applicationId} is ever persisted from the envelope — the rest of
 *       {@code request.application()} is handed to the worker and never stored;</li>
 *   <li>a repeated {@code /execute} for the same id is idempotent — one row, no re-processing;</li>
 *   <li>the one decision this use case's own field table assigns to it — the consent gate
 *       ({@code consents.termsAccepted} false → {@code DECLINED}, nothing generated, nothing
 *       sent) — runs off-thread, after the row is committed.</li>
 * </ol>
 *
 * <p>UC02 (Review Agreement) reads the fuller shape ({@code reference}, {@code termsVersion}, the
 * limits, the timeline) back out — this service is what populates those columns, once, at
 * generation.</p>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    /** Used only when no {@link AgreementConfig} row exists at all — should never happen outside a broken seed. */
    private static final int DEFAULT_EXPIRY_DAYS = 5;

    private final Executor executor;
    private final AgreementRecordRepository agreementRecords;
    private final AgreementConfigRepository agreementConfigs;
    private final AgreementStatusHistoryRepository history;
    private final OrchestratorClient orchestrator;
    private final AgreementDocumentComposer agreementDocuments;
    private final EsignProvider esignProvider;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              AgreementRecordRepository agreementRecords,
                              AgreementConfigRepository agreementConfigs,
                              AgreementStatusHistoryRepository history,
                              OrchestratorClient orchestrator,
                              AgreementDocumentComposer agreementDocuments,
                              EsignProvider esignProvider) {
        this.executor = executor;
        this.agreementRecords = agreementRecords;
        this.agreementConfigs = agreementConfigs;
        this.history = history;
        this.orchestrator = orchestrator;
        this.agreementDocuments = agreementDocuments;
        this.esignProvider = esignProvider;
    }

    /**
     * The hand-off point. Insert the row on THIS (request) thread — it is one small write, not
     * "rule or provider work" — then return so the controller can send the {@code 202}; the
     * decision itself moves to the worker pool.
     *
     * <p><b>Nothing after the row is saved may block the caller.</b> The orchestrator is holding a
     * connection open; only the insert happens inline, everything else runs on
     * {@code applicationTaskExecutor}.</p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        String applicationId = request.applicationId();
        log.info("RECEIVED {}", request.summary());

        if (!ensureAgreementRecord(applicationId)) {
            // Idempotent replay: the row already exists (first call, a retry, or a duplicate
            // delivery), so there is nothing new to do — no second row, no second decision.
            log.info("DUPLICATE /execute for {} — one row already exists, not reprocessing",
                    applicationId);
            return;
        }

        executor.execute(() -> decide(request));
    }

    /**
     * Insert exactly one {@link AgreementRecord}, keyed by {@code applicationId}, in
     * {@link AgreementStatus#GENERATING}. Returns {@code false} without throwing when the row
     * already exists — same request twice, same result once.
     *
     * <p>A save-then-catch, not a check-then-save: two concurrent {@code /execute} calls for the
     * same id can both pass an {@code existsById} check before either commits, so the primary key
     * itself is the source of truth and the unique-constraint violation is the expected,
     * non-exceptional outcome of losing that race.</p>
     */
    private boolean ensureAgreementRecord(String applicationId) {
        if (agreementRecords.existsById(applicationId)) {
            return false;
        }
        try {
            agreementRecords.save(new AgreementRecord(applicationId, AgreementStatus.GENERATING));
            return true;
        } catch (DataIntegrityViolationException raced) {
            // Lost the race to a concurrent /execute for the same applicationId — its insert won,
            // ours is the duplicate. One row either way.
            log.info("Concurrent /execute for {} — unique constraint caught the duplicate",
                    applicationId);
            return false;
        }
    }

    /**
     * The off-thread decision. Package-private so a unit test can call it directly on the test
     * thread — no Spring, no executor needed to exercise the rule.
     *
     * <p>Runs only after {@link #processApplicationAsync} has committed the row — "the off-thread
     * decision starts only after the row is committed" is the whole point of splitting the two
     * methods.</p>
     *
     * <p>Only the consent gate is decided here (see class javadoc): {@code termsAccepted} false
     * moves the row to {@link AgreementStatus#DECLINED} and reports {@code REJECTED} — nothing
     * is generated for a case that never gets one. Otherwise (accepted, or not yet gated) this
     * pins the case's terms from the current {@link AgreementConfig}, generates UC05's agreement
     * PDF with those terms printed on it, and hands off to {@link #sendForSignature} via UC 07's
     * e-sign provider.</p>
     */
    void decide(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            Application.Consents consents = request.application().consents();
            Boolean termsAccepted = consents == null ? null : consents.termsAccepted();

            if (Boolean.FALSE.equals(termsAccepted)) {
                updateStatus(applicationId, AgreementStatus.DECLINED);
                history.save(new AgreementStatusHistory(applicationId, AgreementStatus.GENERATING,
                        AgreementStatus.DECLINED, "CONSENT_GATE", "system", Instant.now()));
                orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED,
                        "consent not given: termsAccepted=false");
                return;
            }

            // Accepted, or not yet gated: pin this case's terms, generate the agreement document,
            // then send it for signature via UC 07's e-sign provider.
            AgreementConfig config = currentConfig();
            Application.Product product = request.application().product();
            Integer approvedLimit = product == null ? null : product.requestedCreditLimit();
            Integer minPaymentGbp = approvedLimit == null ? null : config.minPaymentFor(approvedLimit);
            pinTerms(applicationId, reference(applicationId), config.getTermsVersion(),
                    approvedLimit, config.getAprPercent(), minPaymentGbp);

            String productCode = product == null ? null : product.productCode();
            String documentSha = agreementDocuments.compose(applicationId,
                    new AgreementDocumentComposer.Content(signerName(request), productCode,
                            approvedLimit, config.getAprPercent(), minPaymentGbp,
                            config.getTermsVersion()));
            sendForSignature(applicationId, signerName(request), documentSha, config);
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator waits out its timeout with
            // nothing to explain it. Refer it to a human and say why — and move the case OFF
            // GENERATING, or it sits stuck there forever with no operator action available (the
            // same reasoning as sendForSignature's own catch, just for a failure earlier in the
            // pipeline: pinning terms or composing the document, e.g. an applicant name PDFBox's
            // WinAnsi-encoded Helvetica cannot render).
            log.error("decide failed for {} — referring", applicationId, e);
            try {
                updateStatus(applicationId, AgreementStatus.PENDING);
                history.save(new AgreementStatusHistory(applicationId, AgreementStatus.GENERATING,
                        AgreementStatus.PENDING, "AGR_GENERATION_FAILED", "system", Instant.now()));
            } catch (RuntimeException alsoFailed) {
                // The original failure was the repository itself (e.g. the SIM-06 "database on
                // fire" case) — nothing to persist to, but the orchestrator still must not be
                // left to time out.
                log.error("also failed to move {} off GENERATING", applicationId, alsoFailed);
            }
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /**
     * UC 07's wiring: register an envelope with the e-sign provider, stamp the case
     * {@code GENERATING → PENDING} with that envelope and its expiry, tell the orchestrator that
     * we are waiting rather than done, then let the provider play its auto-mode (AC 2/3/4) — in
     * that order, because
     * {@link EsignProvider#playAutoMode} must never run before the case is actually PENDING (an
     * {@code INSTANT} auto-event arriving first would find {@code SignatureEventService} still
     * looking at {@code GENERATING} and refuse it with a 409).
     *
     * <p>AC 7: if the provider itself cannot be reached (any {@link RuntimeException} out of
     * {@link EsignProvider#registerEnvelope}), the case still moves to {@code PENDING} — the
     * document exists, it is simply un-sent — and the orchestrator is told {@code REFERRED} with
     * {@code AGR_PROVIDER_UNAVAILABLE} rather than left to time out.</p>
     */
    private void sendForSignature(String applicationId, String signerName, String documentSha,
            AgreementConfig config) {
        try {
            EnvelopeRegistration registration =
                    esignProvider.registerEnvelope(applicationId, documentSha, signerName);
            Instant sentAt = Instant.now();
            Instant expiresAt = sentAt.plusSeconds(expirySeconds(registration.config(), config));
            markSentForSignature(applicationId, registration.envelopeId(), sentAt, expiresAt);
            history.save(new AgreementStatusHistory(applicationId, AgreementStatus.GENERATING,
                    AgreementStatus.PENDING, "ENVELOPE_SENT", "system", sentAt));
            orchestrator.applicationStatusUpdate(applicationId, Decision.PENDING,
                    "agreement sent for signature — waiting for the customer");
            esignProvider.playAutoMode(applicationId, registration);
        } catch (RuntimeException esignDown) {
            log.warn("e-sign provider unreachable for {} — referring for manual handling",
                    applicationId, esignDown);
            updateStatus(applicationId, AgreementStatus.PENDING);
            history.save(new AgreementStatusHistory(applicationId, AgreementStatus.GENERATING,
                    AgreementStatus.PENDING, "AGR_PROVIDER_UNAVAILABLE", "system", Instant.now()));
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "AGR_PROVIDER_UNAVAILABLE — e-sign provider unreachable, application-manual");
        }
    }

    private static String signerName(ApplicationRequest request) {
        Application.Applicant applicant = request.application().applicant();
        return applicant == null ? null : applicant.fullName();
    }

    /** Deterministic and idempotent: the same {@code applicationId} always yields the same reference. */
    private static String reference(String applicationId) {
        int hash = Math.abs(applicationId.hashCode()) % 1_000_000;
        return String.format("agr-%06d", hash);
    }

    private AgreementConfig currentConfig() {
        return agreementConfigs.findTopByOrderByVersionDesc()
                .orElseGet(() -> new AgreementConfig(0, "unversioned", DEFAULT_EXPIRY_DAYS,
                        java.math.BigDecimal.ZERO, 0, java.math.BigDecimal.ZERO));
    }

    private static int expirySeconds(EsignConfigView esignConfig, AgreementConfig agreementConfig) {
        Integer demoExpirySeconds = esignConfig.demoExpirySeconds();
        if (demoExpirySeconds != null) {
            return demoExpirySeconds;
        }
        return agreementConfig.getExpiryDays() * 24 * 3600;
    }

    // Deliberately not relying on @Transactional + dirty-checking here: decide() calls this via
    // self-invocation (this.updateStatus(...)), which bypasses Spring's proxy entirely, so an
    // @Transactional on this method would do nothing — the record would be loaded, mutated, and
    // then simply discarded, detached, with the change never flushed. An explicit save() goes
    // through the repository's own (proxied) transaction and persists regardless of the caller.
    void updateStatus(String applicationId, AgreementStatus status) {
        agreementRecords.findById(applicationId).ifPresent(record -> {
            record.changeStatus(status);
            agreementRecords.save(record);
        });
    }

    /** Same self-invocation caveat as {@link #updateStatus} — see its comment. */
    private void markSentForSignature(String applicationId, String envelopeId, Instant sentAt,
            Instant expiresAt) {
        agreementRecords.findById(applicationId).ifPresent(record -> {
            record.markSentForSignature(envelopeId, sentAt, expiresAt);
            agreementRecords.save(record);
        });
    }

    /** Same self-invocation caveat as {@link #updateStatus} — see its comment. */
    private void pinTerms(String applicationId, String reference, String termsVersion,
            Integer approvedLimit, java.math.BigDecimal apr, Integer minPaymentGbp) {
        agreementRecords.findById(applicationId).ifPresent(record -> {
            record.pinTerms(reference, termsVersion, approvedLimit, apr, minPaymentGbp);
            agreementRecords.save(record);
        });
    }

    /** Everything this module holds, newest first — what its own UI reads until UC 01 replaces it. */
    @Transactional(readOnly = true)
    public List<AgreementRecordView> findAll() {
        return agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(AgreementRecordView::of)
                .toList();
    }
}
