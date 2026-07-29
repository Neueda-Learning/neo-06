package com.neobank.module.repository;

import com.neobank.module.model.AgreementConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Insert-only and versioned — the current version is always the highest. */
public interface AgreementConfigRepository extends JpaRepository<AgreementConfig, Integer> {

    Optional<AgreementConfig> findTopByOrderByVersionDesc();
}
