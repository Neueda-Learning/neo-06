package com.neobank.module.repository;

import com.neobank.module.model.AgreementRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation from these method names.
 *
 * <p>{@code applicationId} is the {@code @Id} itself (see {@link AgreementRecord}), so
 * {@code existsById}/{@code findById} from {@link JpaRepository} are already this module's
 * idempotency check — no extra {@code existsBy...} method needed.</p>
 */
public interface AgreementRecordRepository extends JpaRepository<AgreementRecord, String> {

    /**
     * Newest first — what the board shows. Tiebreak on {@code applicationId} itself: there's no
     * surrogate numeric id any more (the business key is the primary key), and MySQL
     * {@code TIMESTAMP} truncates to whole seconds, so same-second rows still need a deterministic
     * order or the board would shuffle them between page loads.
     */
    List<AgreementRecord> findAllByOrderByCreatedAtDescApplicationIdDesc();
}
