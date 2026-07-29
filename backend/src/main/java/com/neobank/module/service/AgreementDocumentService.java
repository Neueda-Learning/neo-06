package com.neobank.module.service;

import com.neobank.module.dto.AgreementDocumentContent;
import com.neobank.module.model.OfferDocument;
import com.neobank.module.repository.AgreementRecordRepository;
import com.neobank.module.repository.OfferDocumentRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Backs {@code GET /cases/{id}/document} — see {@code prompts/uc-05-prompt.md} (UC05, Serve the
 * Agreement Document).
 *
 * <h2>Read path only</h2>
 *
 * <p>This class never generates a PDF and never references a PDF library — it only
 * {@code SELECT}s the {@link OfferDocument} row {@link AgreementDocumentComposer} wrote once, at
 * execute-time, and streams those exact bytes back. That separation is what makes byte-identity
 * (inline vs. download, before vs. after signing, …) provable rather than just asserted.</p>
 *
 * <p>Every state a document exists in — {@code PENDING} through {@code SIGNED}, {@code EXPIRED},
 * even a post-event {@code DECLINED} — serves the same row unconditionally (AC6): this is a plain
 * {@code SELECT} by {@code applicationId}, so which lifecycle state the case is currently in never
 * changes the answer. The one case this distinguishes is "no row was ever written": a real
 * {@code AgreementRecord} whose consent gate declined it before generation (AC7, {@code 409}) vs.
 * an {@code applicationId} this module has never heard of at all (AC7, {@code 404}).</p>
 */
@Service
public class AgreementDocumentService {

    private final OfferDocumentRepository offerDocuments;
    private final AgreementRecordRepository agreementRecords;

    public AgreementDocumentService(OfferDocumentRepository offerDocuments,
                                    AgreementRecordRepository agreementRecords) {
        this.offerDocuments = offerDocuments;
        this.agreementRecords = agreementRecords;
    }

    /**
     * @throws NoSuchElementException if {@code applicationId} is not a case this module has ever
     *         seen — {@link com.neobank.module.controller.GlobalExceptionHandler} turns that into
     *         a {@code 404}.
     * @throws AgreementDocumentNotGeneratedException if the case exists but is a consent-gate case
     *         — nothing was ever generated for it — turned into a {@code 409}.
     */
    public AgreementDocumentContent getDocument(String applicationId) {
        return offerDocuments.findByApplicationId(applicationId)
                .map(AgreementDocumentService::contentOf)
                .orElseGet(() -> {
                    if (!agreementRecords.existsById(applicationId)) {
                        throw new NoSuchElementException(
                                "no agreement case for applicationId " + applicationId);
                    }
                    throw new AgreementDocumentNotGeneratedException(
                            "no agreement document was ever generated for applicationId "
                                    + applicationId + " — the case was declined at the consent gate");
                });
    }

    private static AgreementDocumentContent contentOf(OfferDocument document) {
        return new AgreementDocumentContent(
                document.getPdfBlob(),
                "application/pdf",
                "agreement-" + document.getApplicationId() + ".pdf");
    }
}


