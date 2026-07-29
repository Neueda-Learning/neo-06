package com.neobank.module.controller;

import com.neobank.module.dto.SignatureEventRequest;
import com.neobank.module.dto.SignatureEventResponse;
import com.neobank.module.service.SignatureEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 06 · Receive Signature Events — the one door every caller uses: the orchestrator-hosted
 * signing page proxying the customer's click, the e-sign mock's auto-modes, and a manual curl all
 * POST here.
 *
 * <p>Not under {@code /api/v1/applications} — that mapping is the fixed orchestrator contract
 * ({@code POST /applications} in, {@code PUT /applications/{id}} out). {@code /cases/...} is this
 * module's own family of endpoints, the one every later use case (search, review, override, …)
 * adds to.</p>
 */
@RestController
@RequestMapping("/cases")
public class SignatureEventController {

    private final SignatureEventService signatureEvents;

    public SignatureEventController(SignatureEventService signatureEvents) {
        this.signatureEvents = signatureEvents;
    }

    /**
     * {@code 200} means the case is now decided — either this call decided it, or it already was
     * (an idempotent replay, {@link SignatureEventResponse#replay}). {@code 404}/{@code 409} come
     * from {@link SignatureEventService#apply} via {@link GlobalExceptionHandler}; an
     * {@code event} outside {@code SIGNED}/{@code DECLINED} never reaches the service at all —
     * Jackson refuses it {@code 400} while binding {@link SignatureEventRequest} (AC 8).
     */
    @PostMapping("/{id}/signature-events")
    public ResponseEntity<SignatureEventResponse> receive(@PathVariable("id") String id,
            @Valid @RequestBody SignatureEventRequest request) {
        return ResponseEntity.ok(signatureEvents.apply(id, request));
    }
}
