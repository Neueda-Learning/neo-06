package com.neobank.module.repository;

import com.neobank.module.model.AgreementStatus;
import com.neobank.module.model.AgreementStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The case timeline, read oldest-first — see
 * {@code module-06-agreement-management-docs/uc-02-review-agreement.md} AC1/AC3: "the full
 * timeline[], oldest first".
 */
public interface AgreementStatusHistoryRepository extends JpaRepository<AgreementStatusHistory, Long> {

    List<AgreementStatusHistory> findByApplicationIdOrderByOccurredAtAsc(String applicationId);

    /**
     * UC 04's {@code envelopeCount}: "how many times the bank has tried" — every row that landed
     * the case on {@code PENDING} (the original send plus every resend/revive), derived from the
     * timeline rather than stored.
     */
    long countByApplicationIdAndToStatus(String applicationId, AgreementStatus toStatus);
}

