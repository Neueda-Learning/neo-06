package com.neobank.module.service;

import com.neobank.module.dto.AgreementDocumentContent;
import com.neobank.module.model.OfferDocument;
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
 * <p>Still skipped: the consent-gate {@code 409} (there is no consent-gate concept yet) and the
 * per-status behaviour beyond "found → 200, not found → 404". An {@code applicationId} for which
 * nothing was ever generated — including one that was never {@code POST}ed to this module — is a
 * plain {@code 404}.</p>
 */
@Service
public class AgreementDocumentService {

    private final OfferDocumentRepository offerDocuments;

    public AgreementDocumentService(OfferDocumentRepository offerDocuments) {
        this.offerDocuments = offerDocuments;
    }

    /**
     * @throws NoSuchElementException if no {@code OfferDocument} was ever generated for
     *         {@code applicationId} — {@link com.neobank.module.controller.GlobalExceptionHandler}
     *         turns that into a {@code 404}.
     */
    public AgreementDocumentContent getDocument(String applicationId) {
        OfferDocument document = offerDocuments.findByApplicationId(applicationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "no agreement document for applicationId " + applicationId));
        return new AgreementDocumentContent(
                document.getPdfBlob(),
                "application/pdf",
                "agreement-" + applicationId + ".pdf");
    }
}

