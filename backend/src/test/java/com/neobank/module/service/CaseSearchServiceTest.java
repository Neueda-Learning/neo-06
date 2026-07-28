package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.applicantlookup.ApplicantLookupClient;
import com.neobank.module.model.AgreementRecord;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.repository.AgreementRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/**
 * UC 01 · Search Cases — the service logic, tested with no Spring and no database.
 *
 * <p>Each AC gets its own test: empty-by-default (AC1), the 10-row cap and the "more" flag
 * (AC2), id search local-only (AC3), name search via the orchestrator (AC3), the status
 * filter including GENERATING (AC6), and no-matches-is-empty-not-500 (AC7).</p>
 */
class CaseSearchServiceTest {

    private AgreementRecordRepository agreementRecords;
    private ApplicantLookupClient applicantLookup;
    private CaseSearchService service;

    @BeforeEach
    void setUp() {
        agreementRecords = org.mockito.Mockito.mock(AgreementRecordRepository.class);
        applicantLookup = org.mockito.Mockito.mock(ApplicantLookupClient.class);
        service = new CaseSearchService(agreementRecords, applicantLookup);
    }

    // AC1 — empty by default; no query, no rows fetched, no orchestrator call.
    @Test
    void aBlankQueryReturnsEmptyAndFetchesNothing() {
        CaseSearchService.SearchResult result = service.search(null, null, 10);

        assertThat(result.rows()).isEmpty();
        assertThat(result.more()).isFalse();
        verify(agreementRecords, never()).searchByApplicationId(any(), any(), any());
        verify(agreementRecords, never()).searchByApplicationIds(any(), any(), any());
        verify(applicantLookup, never()).findApplicationIdsByName(any());
    }

    @Test
    void aWhitespaceOnlyQueryIsAlsoEmpty() {
        assertThat(service.search("   ", null, 10).rows()).isEmpty();
    }

    // AC2 — at most 10 matches, newest first; an 11th means "more".
    @Test
    void theTenthMatchIsReturnedAndTheEleventhSetsMore() {
        // The service fetches limit + 1 to detect "more" without a second query. Eleven rows
        // back means there IS an eleventh — trim to ten and flag.
        when(agreementRecords.searchByApplicationId(eq("SIM"), any(), any()))
                .thenReturn(rows(11, "SIM"));

        CaseSearchService.SearchResult result = service.search("SIM", null, 10);

        assertThat(result.rows()).hasSize(10);
        assertThat(result.more()).isTrue();
    }

    @Test
    void exactlyTenMatchesMeansNoMore() {
        when(agreementRecords.searchByApplicationId(eq("SIM"), any(), any()))
                .thenReturn(rows(10, "SIM"));

        CaseSearchService.SearchResult result = service.search("SIM", null, 10);

        assertThat(result.rows()).hasSize(10);
        assertThat(result.more()).isFalse();
    }

    @Test
    void aLimitAboveTenIsClampedToTen() {
        // AC2 says "at most 10" — the caller asking for 50 still gets 10, and the page size
        // passed to the repository is 11 (10 + 1 for the "more" probe).
        when(agreementRecords.searchByApplicationId(eq("SIM"), any(), any()))
                .thenReturn(rows(11, "SIM"));

        service.search("SIM", null, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(agreementRecords).searchByApplicationId(eq("SIM"), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(11);
    }

    @Test
    void aNonPositiveLimitDefaultsToTen() {
        when(agreementRecords.searchByApplicationId(eq("SIM"), any(), any()))
                .thenReturn(rows(2, "SIM"));

        service.search("SIM", null, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(agreementRecords).searchByApplicationId(eq("SIM"), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(11); // 10 + 1 probe
    }

    // AC3 — id search is local-only; name search goes through the orchestrator first.
    @Test
    void idSearchHitsTheLocalTableAndNeverTheOrchestrator() {
        when(agreementRecords.searchByApplicationId(eq("SIM-01"), any(), any()))
                .thenReturn(List.of(new AgreementRecord("SIM-01", AgreementStatus.GENERATING)));

        CaseSearchService.SearchResult result = service.search("SIM-01", null, 10);

        assertThat(result.rows()).singleElement()
                .satisfies(view -> assertThat(view.applicationId()).isEqualTo("SIM-01"));
        verify(applicantLookup, never()).findApplicationIdsByName(any());
        verify(agreementRecords, never()).searchByApplicationIds(any(), any(), any());
    }

    @Test
    void nameSearchFallsThroughToTheOrchestratorWhenIdSearchFindsNothing() {
        // Id search returns nothing — the term is treated as a name instead.
        when(agreementRecords.searchByApplicationId(eq("Maria"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("Maria"))
                .thenReturn(List.of("SIM-01", "SIM-02"));
        when(agreementRecords.searchByApplicationIds(any(), any(), any()))
                .thenReturn(List.of(
                        new AgreementRecord("SIM-01", AgreementStatus.SIGNED),
                        new AgreementRecord("SIM-02", AgreementStatus.PENDING)));

        CaseSearchService.SearchResult result = service.search("Maria", null, 10);

        assertThat(result.rows()).extracting(v -> v.applicationId())
                .containsExactly("SIM-01", "SIM-02");
        verify(applicantLookup).findApplicationIdsByName("Maria");
    }

    // AC5 — the "Maria" story: name → ids → local rows.
    @Test
    void searchingMariaResolvesToHerCasesViaTheOrchestrator() {
        when(agreementRecords.searchByApplicationId(eq("Maria"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("Maria"))
                .thenReturn(List.of("SIM-01"));
        when(agreementRecords.searchByApplicationIds(any(), any(), any()))
                .thenReturn(List.of(new AgreementRecord("SIM-01", AgreementStatus.SIGNED)));

        CaseSearchService.SearchResult result = service.search("Maria", null, 10);

        assertThat(result.rows()).singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("SIGNED");
                    // termsVersion / sentAt / signedAt belong to later use cases — null until then.
                    assertThat(view.termsVersion()).isNull();
                    assertThat(view.sentAt()).isNull();
                    assertThat(view.signedAt()).isNull();
                });
    }

    // AC6 — status filter, including internal GENERATING.
    @Test
    void theStatusFilterIsPassedThroughToTheIdSearch() {
        when(agreementRecords.searchByApplicationId(eq("SIM"), eq(AgreementStatus.GENERATING), any()))
                .thenReturn(List.of(new AgreementRecord("SIM-GEN", AgreementStatus.GENERATING)));

        CaseSearchService.SearchResult result = service.search("SIM", AgreementStatus.GENERATING, 10);

        assertThat(result.rows()).singleElement()
                .satisfies(view -> assertThat(view.status()).isEqualTo("GENERATING"));
        verify(agreementRecords).searchByApplicationId(eq("SIM"), eq(AgreementStatus.GENERATING), any());
    }

    @Test
    void theStatusFilterAlsoAppliesToNameSearch() {
        when(agreementRecords.searchByApplicationId(eq("Maria"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("Maria"))
                .thenReturn(List.of("SIM-01"));
        when(agreementRecords.searchByApplicationIds(any(), eq(AgreementStatus.SIGNED), any()))
                .thenReturn(List.of(new AgreementRecord("SIM-01", AgreementStatus.SIGNED)));

        service.search("Maria", AgreementStatus.SIGNED, 10);

        verify(agreementRecords).searchByApplicationIds(any(), eq(AgreementStatus.SIGNED), any());
    }

    // AC7 — no matches is [], never an exception. Orchestrator down → empty name search.
    @Test
    void noMatchesReturnsEmptyNotAnException() {
        when(agreementRecords.searchByApplicationId(eq("nope"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("nope"))
                .thenReturn(List.of());

        CaseSearchService.SearchResult result = service.search("nope", null, 10);

        assertThat(result.rows()).isEmpty();
        assertThat(result.more()).isFalse();
    }

    @Test
    void anOrchestratorFailureSurfacesAsEmptyNotAnException() {
        // ApplicantLookupClient already swallows its own exceptions, but the service must not
        // propagate anything either — a search never becomes a 500.
        when(agreementRecords.searchByApplicationId(eq("Maria"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("Maria"))
                .thenReturn(List.of()); // what the client returns after swallowing the error

        CaseSearchService.SearchResult result = service.search("Maria", null, 10);

        assertThat(result.rows()).isEmpty();
    }

    @Test
    void nameSearchSkipsTheRepositoryWhenTheOrchestratorReturnsNoIds() {
        // No point issuing an IN () query — the service short-circuits.
        when(agreementRecords.searchByApplicationId(eq("Nobody"), any(), any()))
                .thenReturn(List.of());
        when(applicantLookup.findApplicationIdsByName("Nobody"))
                .thenReturn(List.of());

        service.search("Nobody", null, 10);

        verify(agreementRecords, never()).searchByApplicationIds(any(), any(), any());
    }

    private static List<AgreementRecord> rows(int count, String prefix) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new AgreementRecord(prefix + "-" + i, AgreementStatus.GENERATING))
                .toList();
    }
}
