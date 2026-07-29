package com.neobank.module.controller;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.ResendRequest;
import com.neobank.module.service.ResendService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 04 · {@code POST /cases/{id}/resend}. A sibling of {@link CasesController} and
 * {@link SignatureEventController} under the same {@code /cases} family — see
 * {@code docs/uc-04-pending-expired-queue.md}.
 */
@RestController
@RequestMapping("/cases")
public class ResendController {

    private final ResendService resend;

    public ResendController(ResendService resend) {
        this.resend = resend;
    }

    @PostMapping("/{id}/resend")
    public CaseDetailView resend(@PathVariable("id") String id, @Valid @RequestBody ResendRequest request) {
        return resend.resend(id, request.operator());
    }
}
