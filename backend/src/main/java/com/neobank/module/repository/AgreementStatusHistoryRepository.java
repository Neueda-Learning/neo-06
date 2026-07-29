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
     * UC 04's {@code envelopeCount}: how many times an envelope has been (re)sent for this case —
     * derived, not stored, per the brief's "从历史行动态统计得出，不额外存储字段". Every send/resend ends in
     * {@code toStatus == PENDING} (the first send from {@code GENERATING}, a resend from
     * {@code PENDING} or {@code EXPIRED}), so counting rows that land there counts envelopes.
     */
    long countByApplicationIdAndToStatus(String applicationId, AgreementStatus toStatus);
}
