package com.neobank.module.repository;

import com.neobank.module.model.EsignProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** UC 07 — always exactly one row, {@link EsignProviderConfig#SINGLETON_ID}. */
public interface EsignProviderConfigRepository extends JpaRepository<EsignProviderConfig, Long> {
}
