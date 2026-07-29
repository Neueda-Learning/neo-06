package com.neobank.module.controller;

import com.neobank.module.dto.QueueEntryView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.service.QueueService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 04 · Pending/Expired Queue — {@code GET /queue?state=PENDING|EXPIRED&limit=10}. See
 * {@code docs/uc-04-pending-expired-queue.md}.
 *
 * <p>{@code state} outside {@link AgreementStatus}'s names (e.g. {@code SIGNED}, or a typo) is
 * refused {@code 400} by Spring's own enum binding before this method ever runs — no extra
 * validation needed here.</p>
 */
@RestController
public class QueueController {

    private final QueueService queue;

    public QueueController(QueueService queue) {
        this.queue = queue;
    }

    @GetMapping("/queue")
    public List<QueueEntryView> getQueue(@RequestParam AgreementStatus state,
            @RequestParam(defaultValue = "10") int limit) {
        return queue.findQueue(state, limit);
    }
}
