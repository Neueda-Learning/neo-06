package com.neobank.module.service;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.dto.SignatureEventResponse;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.model.SignatureEventType;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 06 · Receive Signature Events — the engine of this module.</h2>
 *
 * <p>The whole state machine lives in {@link #apply}, following the build note's guard order
 * exactly: case exists → {@code envelopeId} matches the CURRENT envelope → state is
 * {@code PENDING} → apply. Two things ride alongside every real transition — one
 * {@link AgreementStatusHistory} row and exactly one callback to the orchestrator — and neither
 * fires on an idempotent replay.</p>
 *
 * <p>{@code SIGNED} and {@code DECLINED} are this module's own two-outcome vocabulary
 * ({@link SignatureEventType}); mapping them onto the fixed, three-value
 * {@link Decision} the orchestrator understands is this class's job: {@code SIGNED} is the
 * module's {@link Decision#ACCEPTED} (the contract is now in force), {@code DECLINED} is
 * {@link Decision#REJECTED}. {@code EXPIRED} is deliberately absent from
 * {@link SignatureEventType} — the expiry clock is this module's own (UC 04), never a caller's to
 * report (AC 8), so it is out of scope here.</p>
 */
@Service
public class SignatureEventService {

    private static final Logger log = LoggerFactory.getLogger(SignatureEventService.class);

    private static final String ACTOR_CUSTOMER = "customer";

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;
    private final OrchestratorClient orchestrator;

    public SignatureEventService(AgreementRecordRepository agreementRecords,
                                 AgreementStatusHistoryRepository history,
                                 OrchestratorClient orchestrator) {
        this.agreementRecords = agreementRecords;
        this.history = history;
        this.orchestrator = orchestrator;
    }

    /**
     * Apply one signature event, exactly once as far as the module is concerned however many
     * times the network delivers it.
     *
     * @throws NoSuchElementException          unknown {@code applicationId} — 404 (AC 6)
     * @throws SignatureEventConflictException stale envelope, wrong state, or a contradicting
     *                                          terminal decision — 409 (AC 4, 5, 6)
     */
    @Transactional
    public SignatureEventResponse apply(String applicationId, SignatureEventRequest request) {
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("no such case: " + applicationId));

        if (!Objects.equals(record.getEnvelopeId(), request.envelopeId())) {
            throw new SignatureEventConflictException(
                    "envelope " + request.envelopeId() + " is not the current envelope for "
                            + applicationId);
        }

        AgreementStatus resultingStatus = statusFor(request.event());

        if (record.getStatus() == resultingStatus) {
            // AC 3: the identical event, replayed — no new history row, no second callback.
            log.info("REPLAY {} {} on {} — already decided, nothing to do",
                    applicationId, request.event(), record.getStatus());
            return SignatureEventResponse.replay(applicationId, record.getStatus());
        }

        if (record.getStatus() == AgreementStatus.EXPIRED) {
            // AC 4: the clock got there first. The case stays EXPIRED, but the refusal is audit
            // too — one history row records the late event even though nothing about the case
            // itself moves.
            appendHistory(applicationId, AgreementStatus.EXPIRED, AgreementStatus.EXPIRED,
                    "REFUSED_LATE_EVENT");
            throw new SignatureEventConflictException(
                    "case " + applicationId + " already EXPIRED — late " + request.event()
                            + " refused");
        }

        if (record.getStatus() != AgreementStatus.PENDING) {
            // AC 6: GENERATING, DECLINED, or a contradicting SIGNED — none of them are PENDING.
            throw new SignatureEventConflictException(
                    "case " + applicationId + " is " + record.getStatus()
                            + ", not PENDING — cannot apply " + request.event());
        }

        AgreementStatus fromStatus = record.getStatus();
        if (request.event() == SignatureEventType.SIGNED) {
            record.sign(request.occurredAt());
        } else {
            record.changeStatus(AgreementStatus.DECLINED);
        }
        appendHistory(applicationId, fromStatus, resultingStatus, "SIGNATURE_EVENT");
        reportToOrchestrator(applicationId, request.event());

        return SignatureEventResponse.applied(applicationId, resultingStatus);
    }

    private static AgreementStatus statusFor(SignatureEventType event) {
        return event == SignatureEventType.SIGNED ? AgreementStatus.SIGNED : AgreementStatus.DECLINED;
    }

    private void appendHistory(String applicationId, AgreementStatus fromStatus,
                               AgreementStatus toStatus, String event) {
        history.save(new AgreementStatusHistory(applicationId, fromStatus, toStatus, event,
                ACTOR_CUSTOMER, Instant.now()));
    }

    /** Callback 2 — the module's real, final answer, deferred until now on purpose (see UC 00). */
    private void reportToOrchestrator(String applicationId, SignatureEventType event) {
        if (event == SignatureEventType.SIGNED) {
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "agreement signed by the customer");
        } else {
            orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED,
                    "AGR_DECLINED_BY_CUSTOMER — customer declined to sign the agreement");
        }
    }
}
