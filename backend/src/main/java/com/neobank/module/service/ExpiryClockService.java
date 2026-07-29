package com.neobank.module.service;

import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.AgreementStatusHistoryRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The expiry clock UC 07's AC5 needs actually ticking: "SILENT + demoExpirySeconds 30 → the case
 * sits PENDING, then ~30s later EXPIRED" is only observable if something sweeps PENDING cases past
 * their {@code expiresAt}.
 *
 * <p>{@code docs/uc-04-pending-expired-queue.md} (the Pending &amp; Expired Queue / resend screen)
 * is where this naturally belongs long-term — same {@code AgreementRecord}/{@code expiresAt} data,
 * same {@code AgreementStatusHistory} event shape — but nothing in that use case's own build
 * actually flips the status yet (it only reads and resends). This class is the smallest addition
 * that makes AC5 demoable today; UC 04's plan is free to absorb, extend or replace it outright —
 * nothing here is UC 07-specific beyond the reason it had to exist now.</p>
 */
@Service
public class ExpiryClockService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryClockService.class);

    private final AgreementRecordRepository agreementRecords;
    private final AgreementStatusHistoryRepository history;

    public ExpiryClockService(AgreementRecordRepository agreementRecords,
                              AgreementStatusHistoryRepository history) {
        this.agreementRecords = agreementRecords;
        this.history = history;
    }

    /**
     * A 5s tick is generous next to the demo's own dial (measured in tens of seconds) and cheap
     * next to eleven services sharing one small connection pool (see {@code application.yml}'s own
     * note on the Hikari budget) — this is a handful of rows read once every five seconds, not a
     * heavy poll.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        agreementRecords.findByStatusAndExpiresAtBefore(AgreementStatus.PENDING, now)
                .forEach(record -> {
                    String applicationId = record.getApplicationId();
                    record.changeStatus(AgreementStatus.EXPIRED);
                    agreementRecords.save(record);
                    history.save(new AgreementStatusHistory(applicationId, AgreementStatus.PENDING,
                            AgreementStatus.EXPIRED, "EXPIRY_CLOCK", "system", now));
                    log.info("EXPIRED {} — past its expiresAt with no signature", applicationId);
                });
    }
}
