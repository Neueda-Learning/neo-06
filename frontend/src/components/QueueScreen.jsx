import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  ChipGroup,
  DataTable,
  EmptyState,
  PageHeader,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';

const STATES = ['PENDING', 'EXPIRED'];

/**
 * UC04 · Pending &amp; Expired Queue — the two filtered reads, and the one write (resend) that
 * belongs to this screen rather than the board's detail view: an operator working the queue is
 * clearing a backlog, not reviewing one case.
 */
export default function QueueScreen({ operator }) {
  const [state, setState] = useState('PENDING');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRows(await api.getQueue(state, 10));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [state]);

  useEffect(() => {
    reload();
    const id = setInterval(reload, 5000);
    return () => clearInterval(id);
  }, [reload]);

  const resend = async (applicationId) => {
    setBusyId(applicationId);
    try {
      await api.resendCase(applicationId, operator);
      await reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  };

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'state',
      header: 'State',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.state)}>{r.state}</Badge>,
    },
    { key: 'sentAt', header: 'Sent', render: (r) => time(r.sentAt) },
    { key: 'expiresAt', header: 'Expires', render: (r) => time(r.expiresAt) },
    { key: 'ageHours', header: 'Age (h)', numeric: true },
    { key: 'envelopeCount', header: 'Envelopes', numeric: true },
    {
      key: 'actions',
      header: '',
      tight: true,
      render: (r) => (
        <Button
          size="sm"
          disabled={!operator || busyId === r.applicationId}
          onClick={() => resend(r.applicationId)}
        >
          {r.state === 'EXPIRED' ? 'Revive' : 'Resend'}
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Pending & expired queue"
        lede="cases waiting on a signature, oldest first — resend rotates the envelope and resets the clock"
      />

      {error && <Alert tone="negative">{error}</Alert>}

      <Toolbar>
        <ChipGroup options={STATES} value={state} onChange={setState} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.applicationId}
        footnote="oldest first"
        empty={<EmptyState title="Queue clear">Nothing is {state.toLowerCase()} right now.</EmptyState>}
      />
    </>
  );
}
