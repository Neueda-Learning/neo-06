import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Field,
  FormActions,
  FormGrid,
  KeyValue,
  Modal,
  Select,
  Spinner,
  Textarea,
  Timeline,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';
import AgreementPreview from './AgreementPreview.jsx';

const OVERRIDE_TARGETS = {
  PENDING: [{ value: 'DECLINED', label: 'Decline — stop this live offer' }],
  EXPIRED: [{ value: 'DECLINED', label: 'Decline — abandon this case' }],
  DECLINED: [{ value: 'PENDING', label: 'Revive — send it back out for signature' }],
};

/**
 * UC02 (detail) + UC03 (applicant sidebar) + UC05 (document link) + UC04 (resend) + UC08
 * (override), all on one case — the terms, the timeline and the human beside the machine, per
 * the docs' own read: a case is one story told from several use cases.
 */
export default function CaseDetail({ applicationId, operator, onChanged }) {
  const [detail, setDetail] = useState(null);
  const [applicant, setApplicant] = useState(null);
  const [applicantError, setApplicantError] = useState(null);
  const [error, setError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [overrideOpen, setOverrideOpen] = useState(false);
  const [overrideDraft, setOverrideDraft] = useState({ newStatus: '', reason: '' });

  const reload = useCallback(async () => {
    try {
      setDetail(await api.getCase(applicationId));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
    try {
      setApplicant(await api.getApplicant(applicationId));
      setApplicantError(null);
    } catch (e) {
      setApplicant(null);
      setApplicantError(e.message);
    }
  }, [applicationId]);

  useEffect(() => {
    reload();
  }, [reload]);

  if (error) return <Alert tone="negative">{error}</Alert>;
  if (!detail) return <Spinner />;

  const resendable = detail.status === 'PENDING' || detail.status === 'EXPIRED';
  const overrideOptions = OVERRIDE_TARGETS[detail.status] ?? [];

  const resend = async () => {
    setBusy(true);
    setActionError(null);
    try {
      await api.resendCase(applicationId, operator);
      await reload();
      onChanged?.();
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const submitOverride = async (event) => {
    event.preventDefault();
    setBusy(true);
    setActionError(null);
    try {
      await api.overrideCase(applicationId, {
        newStatus: overrideDraft.newStatus,
        reason: overrideDraft.reason,
        operator,
      });
      setOverrideOpen(false);
      await reload();
      onChanged?.();
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="case-detail">
      {actionError && <Alert tone="negative">{actionError}</Alert>}

      <div className="case-detail__grid">
        <div>
          <KeyValue
            items={[
              { label: 'Status', value: <Badge tone={statusTone(detail.status)}>{detail.status}</Badge> },
              { label: 'Reference', value: detail.reference ?? '—', mono: true },
              { label: 'Terms version', value: detail.termsVersion ?? '—' },
              {
                label: 'Approved limit',
                value: detail.approvedLimit != null ? `£${detail.approvedLimit}` : '—',
              },
              { label: 'APR', value: detail.apr != null ? `${detail.apr}%` : '—' },
              {
                label: 'Min. payment',
                value: detail.minPaymentGbp != null ? `£${detail.minPaymentGbp}` : '—',
              },
              { label: 'Envelope', value: detail.envelopeId ?? '—', mono: true },
              { label: 'Sent', value: time(detail.sentAt) },
              { label: 'Expires', value: time(detail.expiresAt) },
              { label: 'Signed', value: time(detail.signedAt) },
            ]}
          />

          <div className="case-detail__actions">
            {detail.documentAvailable ? (
              <>
                <a href={api.documentUrl(applicationId)} target="_blank" rel="noreferrer">
                  <Button variant="ghost" size="sm" type="button">
                    View agreement document
                  </Button>
                </a>
                <a href={api.documentDownloadUrl(applicationId)}>
                  <Button variant="ghost" size="sm" type="button">
                    Download PDF
                  </Button>
                </a>
              </>
            ) : (
              <span className="case-detail__hint">
                No agreement document has been generated for this case yet.
              </span>
            )}
            {resendable && (
              <Button size="sm" onClick={resend} disabled={busy || !operator}>
                Resend
              </Button>
            )}
            {overrideOptions.length > 0 && (
              <Button
                size="sm"
                variant="ghost"
                disabled={!operator}
                onClick={() => {
                  setOverrideDraft({ newStatus: overrideOptions[0].value, reason: '' });
                  setOverrideOpen(true);
                }}
              >
                Override
              </Button>
            )}
            {detail.status === 'SIGNED' && (
              <span className="case-detail__hint">signed cases are never overridden</span>
            )}
          </div>
        </div>

        <div>
          <h4 className="case-detail__subhead">Applicant</h4>
          {applicantError && (
            <Alert tone="warning">Could not reach the orchestrator — {applicantError}</Alert>
          )}
          {applicant && (
            <KeyValue
              items={[
                { label: 'Name', value: applicant.fullName ?? '—' },
                { label: 'Email', value: applicant.email ?? '—' },
                { label: 'Mobile', value: applicant.mobile ?? '—' },
                { label: 'Product', value: applicant.productCode ?? '—' },
                { label: 'Terms accepted', value: applicant.termsAccepted ? 'Yes' : 'No' },
              ]}
            />
          )}
        </div>
      </div>

      {detail.documentAvailable && (
        <>
          <h4 className="case-detail__subhead">Agreement document</h4>
          <AgreementPreview detail={detail} applicant={applicant} />
        </>
      )}

      <h4 className="case-detail__subhead">Timeline</h4>
      <Timeline
        items={(detail.timeline ?? []).map((row) => ({
          title: `${row.fromStatus} → ${row.toStatus} · ${row.event}`,
          detail: row.actor,
          when: time(row.occurredAt),
        }))}
      />

      <Modal
        open={overrideOpen}
        title="Override case"
        onClose={() => setOverrideOpen(false)}
      >
        <form onSubmit={submitOverride}>
          <FormGrid cols={1}>
            <Field label="New status">
              {({ id }) => (
                <Select
                  id={id}
                  options={overrideOptions}
                  value={overrideDraft.newStatus}
                  onChange={(e) => setOverrideDraft({ ...overrideDraft, newStatus: e.target.value })}
                />
              )}
            </Field>
            <Field label="Reason" hint="mandatory — recorded in the override log">
              {({ id }) => (
                <Textarea
                  id={id}
                  value={overrideDraft.reason}
                  onChange={(e) => setOverrideDraft({ ...overrideDraft, reason: e.target.value })}
                  required
                />
              )}
            </Field>
          </FormGrid>
          <FormActions>
            <Button type="button" variant="ghost" onClick={() => setOverrideOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={busy || !overrideDraft.reason}>
              Confirm override
            </Button>
          </FormActions>
        </form>
      </Modal>
    </div>
  );
}
