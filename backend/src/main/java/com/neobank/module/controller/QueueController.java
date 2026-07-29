package com.neobank.module.controller;

import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.service.QueueService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC04 — Pending &amp; Expired Queue: {@code GET /queue?state=PENDING|EXPIRED&limit=10}. See
 * {@code module-06-agreement-management-docs/uc-04-pending-expired-queue.md}. Resend lives on
 * {@link CasesController} ({@code POST /cases/{id}/resend}) since it acts on one case, not the
 * queue as a whole.
 */
@RestController
@RequestMapping("/queue")
public class QueueController {

    private final QueueService queue;

    public QueueController(QueueService queue) {
        this.queue = queue;
    }

    @GetMapping
    public List<QueueEntryView> list(
            @RequestParam String state,
            @RequestParam(defaultValue = "10") int limit) {
        AgreementStatus parsed;
        try {
            parsed = AgreementStatus.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("state must be PENDING or EXPIRED");
        }
        return queue.list(parsed, limit);
    }
}
