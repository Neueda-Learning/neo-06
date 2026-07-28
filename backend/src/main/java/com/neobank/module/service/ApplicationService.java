package com.neobank.module.service;

import com.neobank.module.dto.AgreementRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
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
 * <p>Deciding anything beyond the consent gate (generating the PDF, registering the e-sign
 * envelope, moving {@code GENERATING → PENDING}, the happy-path callback) is the decision engine —
 * a later use case, and explicitly out of scope for this one (see the UC 00 brief's "Out of
 * scope"). What belongs here is exactly what the brief's acceptance criteria ask for:</p>
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
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final AgreementRecordRepository agreementRecords;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              AgreementRecordRepository agreementRecords,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.agreementRecords = agreementRecords;
        this.orchestrator = orchestrator;
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
     * <p>Only the consent gate is decided here (see class javadoc). Everything else the happy
     * path needs — PDF generation, envelope registration, the {@code GENERATING → PENDING} move,
     * and the callback that goes with it — is left for the decision engine use case; the row
     * simply stays {@code GENERATING} until that lands.</p>
     */
    void decide(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            Application application = request.application();
            Application.Consents consents = application == null ? null : application.consents();
            Boolean termsAccepted = consents == null ? null : consents.termsAccepted();

            if (Boolean.FALSE.equals(termsAccepted)) {
                updateStatus(applicationId, AgreementStatus.DECLINED);
                orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED,
                        "consent not accepted — no agreement generated");
                return;
            }

            // Consent gate passed (or the field was absent — nothing to gate on yet). The
            // decision engine (PDF + envelope + PENDING + its callback) is out of scope for UC 00.
            log.info("GENERATING {} — awaiting the decision engine (not yet implemented)",
                    applicationId);
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator waits out its timeout with
            // nothing to explain it. Refer it to a human and say why.
            log.error("decide failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    @Transactional
    void updateStatus(String applicationId, AgreementStatus status) {
        agreementRecords.findById(applicationId).ifPresent(record -> record.changeStatus(status));
    }

    /** Everything this module holds, newest first — what its own UI reads until UC 01 replaces it. */
    @Transactional(readOnly = true)
    public List<AgreementRecordView> findAll() {
        return agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(AgreementRecordView::of)
                .toList();
    }
}
