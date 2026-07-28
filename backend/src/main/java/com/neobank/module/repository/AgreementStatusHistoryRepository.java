package com.neobank.module.repository;

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
}
