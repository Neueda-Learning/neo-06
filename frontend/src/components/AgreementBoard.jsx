import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  PageHeader,
  SearchInput,
  Spinner,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, STATUSES, time } from '../status.js';
import CaseDetail from './CaseDetail.jsx';

const FILTERS = ['All', ...STATUSES];

/**
 * UC01 · Search Cases — the Agreement Board.
 *
 * Empty by default (AC1): nothing renders until a query is typed. A row expands in place
 * (via {@link CaseDetail}) into UC02's full detail, UC03's applicant sidebar, UC04's resend and
 * UC08's override — one case, every use case that touches it, on one screen.
 */
export default function AgreementBoard({ operator }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [result, setResult] = useState({ items: [], more: false });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [expandedId, setExpandedId] = useState(null);

  const search = useCallback(async () => {
    if (!query.trim()) {
      setResult({ items: [], more: false });
      return;
    }
    setLoading(true);
    try {
      const status = filter === 'All' ? undefined : filter;
      setResult(await api.searchCases(query.trim(), status, 10));
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [query, filter]);

  useEffect(() => {
    const id = setTimeout(search, 250);
    return () => clearTimeout(id);
  }, [search]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>,
    },
    { key: 'termsVersion', header: 'Terms', render: (r) => r.termsVersion ?? '—' },
    { key: 'sentAt', header: 'Sent', render: (r) => time(r.sentAt) },
    { key: 'signedAt', header: 'Signed', render: (r) => time(r.signedAt) },
  ];

  return (
    <>
      <PageHeader
        title="Agreement board"
        lede="search a case by application id or applicant name — empty until you search"
      />

      {error && <Alert tone="negative">Could not search — {error}</Alert>}

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id or applicant name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search cases"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} />
      </Toolbar>

      {loading && <Spinner />}

      <DataTable
        columns={columns}
        rows={result.items}
        rowKey={(r) => r.applicationId}
        onRowClick={(r) => setExpandedId(expandedId === r.applicationId ? null : r.applicationId)}
        expandedKey={expandedId}
        renderExpanded={(r) => (
          <CaseDetail applicationId={r.applicationId} operator={operator} onChanged={search} />
        )}
        footnote={result.more ? 'more — refine your search' : undefined}
        empty={
          <EmptyState title={query.trim() ? 'No case matches that' : 'Type to search'}>
            {query.trim()
              ? 'Try the application id, or part of the applicant\u2019s name.'
              : 'This board is empty by default \u2014 search by application id or applicant name.'}
          </EmptyState>
        }
      />
    </>
  );
}
