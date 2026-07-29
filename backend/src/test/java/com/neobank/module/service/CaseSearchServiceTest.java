package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * UC01 — Search Cases. No Spring: {@link AgreementRecordRepository} and {@link ApplicantService}
 * are mocked, so this pins the doc's acceptance criteria without a running app.
 */
class CaseSearchServiceTest {

    private AgreementRecordRepository agreementRecords;
    private ApplicantService applicants;
    private CaseSearchService service;

    @BeforeEach
    void setUp() {
        agreementRecords = mock(AgreementRecordRepository.class);
        applicants = mock(ApplicantService.class);
        service = new CaseSearchService(agreementRecords, applicants);
    }

    @Test
    void blankQueryIsEmptyByDefaultNeverTheWholeTable() {
        CaseSearchResult result = service.search("", null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.more()).isFalse();
        Mockito.verifyNoInteractions(agreementRecords);
    }

    @Test
    void searchingMariaFindsHerSignedCaseByName() {
        // The doc's checkpoint (AC5): searching "Maria" -> her case, SIGNED, termsVersion 2026-06-01.
        Instant sentAt = Instant.parse("2026-07-21T21:41:00Z");
        AgreementRecord maria = new AgreementRecord(
                "app-1234", AgreementStatus.SIGNED, "agr-000123", "env-8f14e45f", "2026-06-01",
                2800, new BigDecimal("24.9"), 84, sentAt, sentAt.plusSeconds(5 * 24 * 3600),
                sentAt.plusSeconds(3600));
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .thenReturn(List.of(maria));
        when(applicants.getApplicant("app-1234"))
                .thenReturn(new ApplicantView("Maria Nowak", "maria.nowak@example.com",
                        "+48123456789", "CREDIT_CARD_REWARDS", true));

        CaseSearchResult result = service.search("Maria", null, 10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).applicationId()).isEqualTo("app-1234");
        assertThat(result.items().get(0).status()).isEqualTo("SIGNED");
        assertThat(result.items().get(0).termsVersion()).isEqualTo("2026-06-01");
        assertThat(result.more()).isFalse();
    }

    @Test
    void searchingByApplicationIdSubstringNeedsNoOrchestratorCall() {
        AgreementRecord record = new AgreementRecord("app-5678", AgreementStatus.PENDING);
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .thenReturn(List.of(record));

        CaseSearchResult result = service.search("5678", null, 10);

        assertThat(result.items()).extracting("applicationId").containsExactly("app-5678");
        Mockito.verifyNoInteractions(applicants);
    }

    @Test
    void anEleventhMatchFlagsMoreRefineYourSearch() {
        List<AgreementRecord> records = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            records.add(new AgreementRecord("case-" + i, AgreementStatus.PENDING));
        }
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc()).thenReturn(records);

        CaseSearchResult result = service.search("case-", null, 10);

        assertThat(result.items()).hasSize(10);
        assertThat(result.more()).isTrue();
    }

    @Test
    void orchestratorDownDuringNameHydrationSkipsThatCandidateNotTheWholeSearch() {
        AgreementRecord record = new AgreementRecord("app-0001", AgreementStatus.PENDING);
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc())
                .thenReturn(List.of(record));
        when(applicants.getApplicant("app-0001"))
                .thenThrow(new OrchestratorUnavailableException("down", null));

        CaseSearchResult result = service.search("Maria", null, 10);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void nameHydrationIsCappedAtTenCalls() {
        List<AgreementRecord> records = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            records.add(new AgreementRecord("case-" + i, AgreementStatus.PENDING));
        }
        when(agreementRecords.findAllByOrderByCreatedAtDescApplicationIdDesc()).thenReturn(records);
        when(applicants.getApplicant(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ApplicantView("Nobody Matching", "x@example.com", null, null, null));

        service.search("no-id-match-here", null, 10);

        verify(applicants, Mockito.times(10)).getApplicant(org.mockito.ArgumentMatchers.anyString());
    }
}
