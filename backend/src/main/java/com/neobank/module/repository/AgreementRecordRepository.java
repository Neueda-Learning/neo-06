package com.neobank.module.repository;

import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

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

    /**
     * UC 01 · Search Cases — id search. Substring match on {@code applicationId} (case-insensitive),
     * with an optional status filter. {@code status = null} means "all statuses" (the JPQL
     * {@code :status IS NULL OR ...} collapses to true). Sort is supplied by the caller via
     * {@code Pageable} so the newest-first ordering is shared with the name-search path.
     *
     * <p>The schema has no applicant column to search — that is the whole reason name search
     * goes through the orchestrator first (see {@code ApplicantLookupClient}). This method
     * touches the local table only, which is what UC 01 AC3 requires for id search.</p>
     */
    @Query("SELECT a FROM AgreementRecord a "
            + "WHERE LOWER(a.applicationId) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "AND (:status IS NULL OR a.status = :status)")
    List<AgreementRecord> searchByApplicationId(@Param("q") String q,
                                                 @Param("status") AgreementStatus status,
                                                 Pageable pageable);

    /**
     * UC 01 · Search Cases — name search. The caller has already resolved a name to a list of
     * application ids via the orchestrator; this loads this module's own rows for those ids,
     * with an optional status filter, newest first. An empty {@code ids} collection returns no
     * rows (JPQL {@code IN ()} is database-portable here because Spring Data never sends an
     * empty list — the service guards it before calling).
     */
    @Query("SELECT a FROM AgreementRecord a "
            + "WHERE a.applicationId IN :ids "
            + "AND (:status IS NULL OR a.status = :status)")
    List<AgreementRecord> searchByApplicationIds(@Param("ids") Collection<String> ids,
                                                  @Param("status") AgreementStatus status,
                                                  Pageable pageable);
    /* UC 04's queue: only the given status, oldest {@code sentAt} first, capped by
     * {@code pageable}'s page size — see {@code QueueService}.
     */
    List<AgreementRecord> findByStatusOrderBySentAtAsc(AgreementStatus status, Pageable pageable);
}
