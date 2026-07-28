package com.neobank.module.service;

import com.neobank.module.dto.AgreementRecordView;
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

// Note: createRecordIfAbsent is deliberately NOT annotated @Transactional at the method level.
// Self-invocation (this.createRecordIfAbsent(...)) bypasses Spring's proxy, so that annotation
// would do nothing anyway — the real transaction boundary is Spring Data JPA's own repository
// proxy, which already wraps each existsById/save call. The TOCTOU window between the two calls
// is closed by the unique primary key + the catch below, not by a wider transaction.

/**
 * <h2>UC00 — Process Application: the durable hand-off between the request thread and the worker
 * that decides.</h2>
 *
 * <p>The controller has already answered {@code 202} by the time this returns. Its whole job is to
 * make one thing true before that happens: exactly one {@link AgreementRecord}, keyed by
 * {@code applicationId}, committed to the database — see
 * {@code module-06-agreement-management-docs/uc-00-process-application.md} AC2. Everything after
 * that commit — deciding, generating, sending — is out of scope here and runs off-thread.</p>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final AgreementRecordRepository agreementRecords;
    private final OrchestratorClient orchestrator;

    /**
     * {@code applicationTaskExecutor} is the thread pool Spring Boot configures for you. Tune it in
     * {@code application.yml} under {@code spring.task.execution.*} — pool size matters once your
     * logic calls a slow mock, because that is what limits how many applications you can handle at
     * once.
     */
    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              AgreementRecordRepository agreementRecords,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.agreementRecords = agreementRecords;
        this.orchestrator = orchestrator;
    }

    /**
     * The hand-off point (AC2, AC6): commit the case row on the request thread — fast, local, no
     * external call — and only then hand the rest of the work to the pool.
     *
     * <p><b>Idempotent (AC4).</b> A repeat {@code /execute} for an {@code applicationId} that
     * already has a row is a no-op: no second row, no second dispatch. The controller still answers
     * {@code 202} either way — see {@code ApplicationController}.</p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        String applicationId = request.applicationId();
        if (!createRecordIfAbsent(applicationId)) {
            log.info("Duplicate /execute for {} — already recorded, not reprocessing", applicationId);
            return;
        }
        executor.execute(() -> processApplication(request));
    }

    /**
     * Insert the {@code GENERATING} row unless one already exists, atomically.
     *
     * <p>{@code existsById} is the fast path; the {@code save} is guarded against a concurrent
     * duplicate too, because two {@code /execute} calls for the same {@code applicationId} can race
     * past the {@code existsById} check on different threads. Either way, the unique primary key is
     * the real guarantee, not a Java one — this method only decides who gets to run the async work.
     *
     * @return {@code true} if this call created the row (and therefore should dispatch the async
     *         worker), {@code false} if the row already existed.
     */
    @Transactional
    boolean createRecordIfAbsent(String applicationId) {
        if (agreementRecords.existsById(applicationId)) {
            return false;
        }
        try {
            agreementRecords.save(new AgreementRecord(applicationId, AgreementStatus.GENERATING));
            return true;
        } catch (DataIntegrityViolationException e) {
            // Lost the race to a concurrent /execute for the same applicationId — its insert won,
            // ours is the duplicate. One row either way.
            log.info("Concurrent /execute for {} — unique constraint caught the duplicate",
                    applicationId);
            return false;
        }
    }

    /**
     * Do the (still placeholder) work: say something, and report something. Storing the row already
     * happened in {@link #createRecordIfAbsent} before this was ever scheduled.
     *
     * <p>Package-private on purpose — the outside world goes through
     * {@link #processApplicationAsync}, and a unit test can call this directly on the test thread,
     * which is what makes it testable without a thread pool.</p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            // 1 — say something. summary() is the one line every module logs on receipt.
            log.info("Hello world from processApplication — {}", request.summary());

            // Confirms the hand-off: the row this method relies on already exists, committed by
            // the request thread. A missing row here is a bug in createRecordIfAbsent, not in the
            // caller.
            agreementRecords.findById(applicationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "no AgreementRecord row for " + applicationId));

            // 2 — report something. Always ACCEPTED until a later use case writes the real rules
            // (deciding is explicitly out of scope for UC00).
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "hello world from processApplication");
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator then waits out its 30s
            // timeout and ends the journey FAILED with nothing to explain it. So: refer it to a
            // human and say why. Keep this guard when you replace the body above.
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /** Everything this module has answered, newest first — what its own UI reads (AC7). */
    @Transactional(readOnly = true)
    public List<AgreementRecordView> findAll() {
        return agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(AgreementRecordView::of)
                .toList();
    }
}
