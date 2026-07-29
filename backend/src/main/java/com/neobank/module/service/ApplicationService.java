package com.neobank.module.service;

import com.neobank.module.dto.AgreementRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
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
 * <p>Deciding anything beyond the consent gate (the real numbers an {@code AgreementConfig} would
 * produce) is the decision engine — a later use case, and explicitly out of scope for this one
 * (see the UC 00 brief's "Out of scope"). What IS done here, once the consent gate has passed:
 * UC05's placeholder agreement PDF is generated (via {@link AgreementDocumentComposer}), the case
 * is sent for signature (UC 04's {@link EnvelopeService} — a MOCK envelope until UC 07 lands a
 * real e-sign provider to call instead), the row moves {@code GENERATING → PENDING}, and the
 * orchestrator is told {@code ACCEPTED} so the journey advances. What belongs here is exactly what
 * the brief's acceptance criteria ask for, plus that:</p>
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
 * limits, the timeline) back out — this service never populates those columns; only a later
 * decision-engine use case does.</p>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final AgreementRecordRepository agreementRecords;
    private final OrchestratorClient orchestrator;
    private final AgreementDocumentComposer agreementDocuments;
    private final AgreementStatusHistoryRepository history;
    private final EnvelopeService envelopes;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              AgreementRecordRepository agreementRecords,
                              OrchestratorClient orchestrator,
                              AgreementDocumentComposer agreementDocuments,
                              AgreementStatusHistoryRepository history,
                              EnvelopeService envelopes) {
        this.executor = executor;
        this.agreementRecords = agreementRecords;
        this.orchestrator = orchestrator;
        this.agreementDocuments = agreementDocuments;
        this.history = history;
        this.envelopes = envelopes;
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
     * generates UC05's placeholder agreement PDF, sends the case for signature (mock envelope,
     * moves the row to {@link AgreementStatus#PENDING}), and reports {@code ACCEPTED} — the REAL
     * priced offer an {@code AgreementConfig} would produce (approvedLimit/apr/termsVersion) is
     * still the decision engine use case's job.</p>
     */
    void decide(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            Application.Consents consents = request.application().consents();
            Boolean termsAccepted = consents == null ? null : consents.termsAccepted();

            if (Boolean.FALSE.equals(termsAccepted)) {
                updateStatus(applicationId, AgreementStatus.DECLINED);
                orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED,
                        "consent not given: termsAccepted=false");
                return;
            }

            // Accepted, or not yet gated: generate the agreement document, send the case for
            // signature (UC 04's EnvelopeService — a mock envelope until UC 07 lands a real
            // provider) and tell the orchestrator so the journey advances.
            agreementDocuments.compose(applicationId, request.application());
            sendForSignature(applicationId);
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "agreement document generated — case moved to PENDING");
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator waits out its timeout with
            // nothing to explain it. Refer it to a human and say why.
            log.error("decide failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
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

    /**
     * UC 00's initial send: registers a mock envelope ({@link EnvelopeService}), moves the row
     * {@code GENERATING → PENDING} with real {@code envelopeId}/{@code sentAt}/{@code expiresAt}
     * values, and appends the {@code SENT} audit row UC 04's queue derives {@code envelopeCount}
     * from. Same self-invocation caveat as {@link #updateStatus} applies — an explicit
     * {@code save()}, not {@code @Transactional} dirty-checking.
     */
    private void sendForSignature(String applicationId) {
        agreementRecords.findById(applicationId).ifPresent(record -> {
            AgreementStatus fromStatus = record.getStatus();
            EnvelopeService.Registration registration = envelopes.register(applicationId);
            record.sendForSignature(registration.envelopeId(), registration.sentAt(),
                    registration.expiresAt());
            agreementRecords.save(record);
            history.save(new AgreementStatusHistory(applicationId, fromStatus, AgreementStatus.PENDING,
                    "SENT", "system", registration.sentAt()));
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
