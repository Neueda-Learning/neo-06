package com.neobank.module.service;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>UC 04's expiry clock — the system actor on the case's {@code PENDING → EXPIRED} edge.</h2>
 *
 * <p>See {@code module-06-agreement-management-docs/uc-04-pending-expired-queue.md}'s state
 * diagram: {@code PENDING --> EXPIRED : expiry clock — pending past the window}. Ticks on a fixed
 * delay (see {@code application.yml}'s {@code agreement.expiry-clock.fixed-delay-ms}), finds every
 * {@code PENDING} case whose {@code expiresAt} has passed, and moves it to {@code EXPIRED}: a
 * history row ({@code EXPIRY_CLOCK}, actor {@code system}) plus callback 2's
 * {@code REFERRED}/{@code application-manual} — see the state diagram's note on {@code PENDING}'s
 * leaving edges.</p>
 */
@Component
public class ExpiryClockService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryClockService.class);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;
    private final OrchestratorClient orchestrator;

    public ExpiryClockService(AgreementRecordRepository agreementRecords,
                              AgreementStatusHistoryRepository history,
                              OrchestratorClient orchestrator) {
        this.agreementRecords = agreementRecords;
        this.history = history;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${agreement.expiry-clock.fixed-delay-ms:30000}")
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        List<AgreementRecord> pending =
                agreementRecords.findByStatusOrderBySentAtAsc(AgreementStatus.PENDING);
        for (AgreementRecord record : pending) {
            if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(now)) {
                expire(record, now);
            }
        }
    }

    private void expire(AgreementRecord record, Instant now) {
        String applicationId = record.getApplicationId();
        record.changeStatus(AgreementStatus.EXPIRED);
        agreementRecords.save(record);
        history.save(new AgreementStatusHistory(applicationId, AgreementStatus.PENDING,
                AgreementStatus.EXPIRED, "EXPIRY_CLOCK", "system", now));
        orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                "AGR_EXPIRED_UNSIGNED — application-manual");
        log.info("EXPIRED {} — past its {} window, unsigned", applicationId, record.getExpiresAt());
    }
}
