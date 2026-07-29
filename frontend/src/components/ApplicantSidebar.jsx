import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, KeyValue, Spinner } from '../design-system';
import { api } from '../api.js';

/**
 * UC03 — View Applicant. Who the agreement is with — the signer's name and contact — without
 * this module ever copying applicant data (see
 * `module-06-agreement-management-docs/uc-03-view-applicant.md`).
 *
 * ⚠️ There is no Agreement Detail screen to attach this to yet (UC 02's frontend isn't built on
 * this branch) — this component is self-contained on purpose. Once that screen exists, mount
 * `<ApplicantSidebar applicationId={...} />` inside it rather than wiring this into `App.jsx`'s
 * nav.
 *
 * One fetch per mount/id change — this panel doesn't poll (the doc doesn't ask for live updates
 * here, unlike the 2s poll `App.jsx` runs for the applications list).
 */
export default function ApplicantSidebar({ applicationId }) {
  const [view, setView] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setView(await api.getApplicant(applicationId));
    } catch (e) {
      setError(e.message);
      setView(null);
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    load();
  }, [load]);

  const retryable = error != null || view?.retryable;

  return (
    <Card title="Applicant" subtitle="live from the orchestrator — never stored here">
      {loading && <Spinner label="Loading applicant" />}

      {!loading && retryable && (
        <Alert
          tone="negative"
          title="Could not load applicant"
          action={
            <Button variant="secondary" size="sm" onClick={load}>
              Retry
            </Button>
          }
        >
          {error ?? 'The orchestrator is unreachable — the case details still work.'}
        </Alert>
      )}

      {!loading && !retryable && view && (
        <KeyValue
          items={[
            { label: 'Full name', value: view.fullName ?? '—' },
            { label: 'Email', value: view.email ?? '—', mono: true },
            { label: 'Mobile', value: view.mobile ?? '—', mono: true },
            { label: 'Product', value: view.productCode ?? '—', mono: true },
            {
              label: 'Terms',
              value: (
                <Badge tone={view.termsAccepted ? 'positive' : 'negative'}>
                  {view.termsAccepted ? 'accepted' : 'not accepted'}
                </Badge>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}
