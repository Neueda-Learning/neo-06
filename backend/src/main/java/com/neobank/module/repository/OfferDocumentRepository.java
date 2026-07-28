package com.neobank.module.repository;

import com.neobank.module.model.OfferDocument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * One row per application. See {@link OfferDocument} for who writes it and who only ever reads
 * it.
 */
public interface OfferDocumentRepository extends JpaRepository<OfferDocument, Long> {

    Optional<OfferDocument> findByApplicationId(String applicationId);
}
