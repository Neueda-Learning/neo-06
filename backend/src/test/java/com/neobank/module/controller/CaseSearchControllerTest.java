package com.neobank.module.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseSearchResultView;
import com.neobank.module.model.AgreementStatus;
import com.neobank.module.service.CaseSearchService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC 01 · Search Cases — the HTTP surface, pinned by a web-slice test.
 *
 * <p>{@link CaseSearchController} only, no service and no database. Each AC has its own test:
 * empty by default (AC1), the bare-array shape and the {@code X-More-Results} header (AC2),
 * the {@code GENERATING} status filter (AC6), and no-matches-is-200-and-empty (AC7).</p>
 */
@WebMvcTest(CaseSearchController.class)
class CaseSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseSearchService search;

    // AC1 — no query, no rows. The board invites a search instead of browsing the whole book.
    @Test
    void noQueryReturnsAnEmptyArray() throws Exception {
        when(search.search(eq(null), any(), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(List.of(), false));

        mvc.perform(get("/api/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // AC2 — at most 10 matches; an 11th sets the "more" header. Body stays a bare array.
    @Test
    void tenMatchesAndNoMoreHeader() throws Exception {
        when(search.search(eq("SIM"), any(), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(tenRows(), false));

        mvc.perform(get("/api/v1/cases").param("q", "SIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(header().doesNotExist("X-More-Results"));
    }

    @Test
    void anEleventhMatchSetsTheMoreHeader() throws Exception {
        when(search.search(eq("SIM"), any(), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(tenRows(), true));

        mvc.perform(get("/api/v1/cases").param("q", "SIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(header().string("X-More-Results", is("true")));
    }

    // AC2 / contract — the row shape is exactly {applicationId, status, termsVersion, sentAt,
    // signedAt}. No applicantName. The three later-use-case fields are present and null here
    // (Jackson's default includes nulls — the shape stays constant as later UCs populate them).
    @Test
    void theRowMatchesTheContractShape() throws Exception {
        CaseSearchResultView row = new CaseSearchResultView(
                "SIM-01", "SIGNED", null, null, null);
        when(search.search(eq("SIM"), any(), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(List.of(row), false));

        mvc.perform(get("/api/v1/cases").param("q", "SIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("SIM-01"))
                .andExpect(jsonPath("$[0].status").value("SIGNED"))
                // The three later-use-case fields are in the payload as null — the shape is
                // constant, the values arrive when UCs 02/05/06 land.
                .andExpect(jsonPath("$[0].termsVersion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].sentAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].signedAt").value(org.hamcrest.Matchers.nullValue()))
                // applicantName is deliberately NOT in the row — the UI hydrates it live.
                .andExpect(jsonPath("$[0].applicantName").doesNotExist());
    }

    // AC6 — status filter, including GENERATING. The enum binds from the query string.
    @Test
    void theStatusFilterBindsIncludingGenerating() throws Exception {
        when(search.search(eq("SIM"), eq(AgreementStatus.GENERATING), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(List.of(), false));

        mvc.perform(get("/api/v1/cases")
                        .param("q", "SIM")
                        .param("status", "GENERATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // AC7 — no matches is 200 + [], never a 500.
    @Test
    void noMatchesIsAnEmptyArrayWithHttp200() throws Exception {
        when(search.search(eq("nope"), any(), anyInt()))
                .thenReturn(new CaseSearchService.SearchResult(List.of(), false));

        mvc.perform(get("/api/v1/cases").param("q", "nope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aLimitParameterIsForwardedToTheService() throws Exception {
        when(search.search(eq("SIM"), any(), eq(5)))
                .thenReturn(new CaseSearchService.SearchResult(List.of(), false));

        mvc.perform(get("/api/v1/cases").param("q", "SIM").param("limit", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void theDefaultLimitIsTen() throws Exception {
        when(search.search(eq("SIM"), any(), eq(10)))
                .thenReturn(new CaseSearchService.SearchResult(List.of(), false));

        mvc.perform(get("/api/v1/cases").param("q", "SIM"))
                .andExpect(status().isOk());
    }

    private static List<CaseSearchResultView> tenRows() {
        return java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new CaseSearchResultView("SIM-" + i, "GENERATING", null, null, null))
                .toList();
    }
}
