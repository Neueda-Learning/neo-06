package com.neobank.module.repository;

import com.neobank.module.model.OverrideLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {
}
