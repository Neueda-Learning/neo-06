package com.neobank.module.repository;

import com.neobank.module.model.AgreementStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation. Insert-only: {@code SignatureEventService} appends one
 * row per transition (or refusal) and nothing in UC 06 reads it back yet — that is UC 02
 * (Review Agreement)'s job, which will add whatever finder it needs when it lands.
 */
public interface AgreementStatusHistoryRepository extends JpaRepository<AgreementStatusHistory, Long> {
}
