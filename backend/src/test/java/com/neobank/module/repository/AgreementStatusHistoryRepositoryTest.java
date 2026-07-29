package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code @DataJpaTest} against in-memory H2 — no Docker needed, runs on plain {@code mvn test}.
 *
 * <p>Pins UC02's AC1/AC3: the timeline comes back oldest first, regardless of insert order or of
 * rows sharing the same {@code occurredAt} second (the same class of ordering bug the surrogate
 * {@code id} tiebreak already guards against elsewhere in this module).</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class AgreementStatusHistoryRepositoryTest {

    @Autowired
    private AgreementStatusHistoryRepository history;

    @Test
    void theTimelineComesBackOldestFirst() {
        Instant first = Instant.parse("2026-07-21T21:41:00Z");
        Instant second = Instant.parse("2026-07-25T10:03:00Z");

        // Insert newest first, on purpose, to prove the query — not insertion order — decides.
        history.saveAndFlush(new AgreementStatusHistory("app-1234", AgreementStatus.PENDING,
                AgreementStatus.SIGNED, "SIGNATURE_EVENT", "customer", second));
        history.saveAndFlush(new AgreementStatusHistory("app-1234", AgreementStatus.GENERATING,
                AgreementStatus.PENDING, "ENVELOPE_SENT", "system", first));

        assertThat(history.findByApplicationIdOrderByOccurredAtAsc("app-1234"))
                .extracting(AgreementStatusHistory::getEvent)
                .containsExactly("ENVELOPE_SENT", "SIGNATURE_EVENT");
    }
}
