package com.neobank.module.repository;

import com.neobank.module.model.OverrideLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The override audit trail — one row per manual override.
 *
 * @see com.neobank.module.model.OverrideLog
 */
public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {
}