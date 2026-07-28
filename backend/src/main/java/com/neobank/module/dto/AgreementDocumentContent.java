package com.neobank.module.dto;

/**
 * What {@code GET /cases/{id}/document} serves — raw bytes plus the headers the controller needs
 * to answer with. See {@code prompts/uc-05-prompt.md} (UC05, Serve the Agreement Document).
 *
 * <p>The bytes come straight from the stored {@code OfferDocument.pdfBlob}, generated once at
 * execute-time by {@code AgreementDocumentComposer}; this record only carries what the controller
 * needs to answer the HTTP request, not the row's own fingerprint
 * ({@code sha256}/{@code sizeBytes}/{@code generatedAt}).</p>
 */
public record AgreementDocumentContent(byte[] content, String contentType, String fileName) {
}
