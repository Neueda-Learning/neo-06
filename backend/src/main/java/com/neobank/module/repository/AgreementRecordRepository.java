package com.neobank.module.repository;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation from these method names.
 *
 * <p>The primary key IS {@code applicationId} (see {@link AgreementRecord}), so
 * {@link JpaRepository#existsById} / {@link JpaRepository#findById} are already this module's
 * idempotency check — no separate {@code existsBy...} method is needed.</p>
 */
public interface AgreementRecordRepository extends JpaRepository<AgreementRecord, String> {

    /**
     * Newest first — what the board shows. Tie-broken by {@code applicationId} itself: this table
     * has no surrogate numeric id, and MySQL {@code TIMESTAMP} truncates to whole seconds, so
     * same-second rows still need a deterministic order or the board would shuffle them between
     * page loads.
     */
    List<AgreementRecord> findAllByOrderByCreatedAtDescApplicationIdDesc();

    /** UC 04's queue: oldest {@code sentAt} first — the cases aging longest surface first. */
    List<AgreementRecord> findByStatusOrderBySentAtAsc(AgreementStatus status);
}

