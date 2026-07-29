package com.neobank.module.service;

import com.neobank.module.dto.AgreementDocumentContent;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
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
 * <p>An {@code applicationId} this module has never heard of is a plain {@code 404}. One this
 * module HAS heard of, but for which no document was ever generated — the consent-gate declined it
 * before generation ran — is a {@code 409}: the case is real, there is simply nothing to serve.</p>
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
     * @throws NoSuchElementException if this module has no case at all for {@code applicationId}
     *         — a {@code 404}.
     * @throws CaseConflictException if the case exists but the consent gate declined it before any
     *         document was generated — a {@code 409}.
     */
    public AgreementDocumentContent getDocument(String applicationId) {
        var document = offerDocuments.findByApplicationId(applicationId);
        if (document.isPresent()) {
            return new AgreementDocumentContent(
                    document.get().getPdfBlob(),
                    "application/pdf",
                    "agreement-" + applicationId + ".pdf");
        }
        AgreementRecord record = agreementRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "no agreement case for applicationId " + applicationId));
        if (record.getStatus() == AgreementStatus.DECLINED) {
            throw new CaseConflictException(
                    "no document was generated for " + applicationId
                            + " — the consent gate declined it before generation");
        }
        throw new NoSuchElementException(
                "no agreement document for applicationId " + applicationId);
    }
}


