package com.neobank.module.repository;

import com.neobank.module.model.AgreementRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation from these method names.
 *
 * <p>The primary key IS {@code applicationId} (see {@link AgreementRecord}), so
 * {@link #existsById} / {@link #findById} are the idempotency check UC 00 needs — no separate
 * lookup-by-application-id method is needed the way the placeholder's surrogate-id table required.</p>
 */
public interface AgreementRecordRepository extends JpaRepository<AgreementRecord, String> {

    /**
     * Newest first — what the board will show. Tie-broken by {@code applicationId} since this
     * table has no separate auto-increment id to settle same-second ties (see
     * {@code DemoShowcaseRepository}'s note on why a tiebreak matters on real MySQL).
     */
    List<AgreementRecord> findAllByOrderByCreatedAtDescApplicationIdDesc();
}
