package com.neobank.module.controller;

import com.neobank.module.dto.AgreementDocumentContent;
import com.neobank.module.service.AgreementDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /cases/{id}/document} — UC05 Serve the Agreement Document, minimal first slice.
 * See {@code prompts/uc-05-prompt.md} and {@link AgreementDocumentService} for what is
 * intentionally hardcoded here and what growing this into the real use case still needs.
 */
@RestController
public class AgreementDocumentController {

    private final AgreementDocumentService documents;

    public AgreementDocumentController(AgreementDocumentService documents) {
        this.documents = documents;
    }

    /**
     * Inline by default; {@code ?download=true} adds a {@code Content-Disposition} attachment
     * header on the same bytes — same body either way, only the header changes.
     */
    @GetMapping("/cases/{id}/document")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable("id") String id,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        AgreementDocumentContent document = documents.getDocument(id);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()));
        if (download) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + document.fileName() + "\"");
        }
        return response.body(document.content());
    }
}
