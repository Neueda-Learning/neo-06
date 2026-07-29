import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Field,
  FormActions,
  FormGrid,
  PageHeader,
  Select,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const POLL_MS = 5000;

const MODES = ['INSTANT', 'DELAYED', 'SILENT'];
const OUTCOMES = ['SIGN', 'DECLINE'];

/**
 * UC 07 · Operate Mock Control Panel — the dials on the e-sign mock: sign instantly, sign after a
 * delay, stay silent, decline, plus a demo-fast expiry window. See
 * `module-06-agreement-management-docs/uc-07-operate-mock-control-panel.md`.
 *
 * Unlike `ApplicantSidebar` (UC03), this panel needs no per-case `applicationId` — it is a
 * standalone settings screen, so it uses the nav item the skeleton always reserved for exactly
 * this (`App.jsx`'s `"settings"` slot).
 *
 * Polls every few seconds while mounted (AC6: "current dial state always visible") so a change
 * made from another tab or a curl call is reflected without a manual refresh — the one screen in
 * this app that benefits from that, since an operator may be demoing from two places at once.
 */
export default function EsignMockPanel() {
  const [config, setConfig] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [draft, setDraft] = useState(null);

  const load = useCallback(async () => {
    try {
      const current = await api.getEsignConfig();
      setConfig(current);
      setError(null);
      // Don't clobber the operator's in-progress edit with a poll — only seed the draft the
      // first time, or after a save this component itself triggered.
      setDraft((prev) => prev ?? toDraft(current));
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(load, POLL_MS);
    return () => clearInterval(id);
  }, [load]);

  const save = useCallback(
    async (e) => {
      e.preventDefault();
      setSaving(true);
      try {
        const applied = await api.putEsignConfig(fromDraft(draft));
        setConfig(applied);
        setDraft(toDraft(applied));
        setError(null);
      } catch (e2) {
        setError(e2.message);
      } finally {
        setSaving(false);
      }
    },
    [draft]
  );

  return (
    <>
      <PageHeader
        title="E-sign Mock Panel"
        lede="sign instantly, sign after a delay, stay silent, or decline — plus a demo-fast expiry window"
        meta={config ? `next envelope: ${config.mode} · ${config.autoOutcome}` : undefined}
      />

      {error && (
        <Alert tone="negative" title="Could not reach the e-sign mock">
          {error}
        </Alert>
      )}

      {draft && (
        <form onSubmit={save}>
          <FormGrid cols={2}>
            <Field label="Mode" htmlFor="esign-mode">
              <Select
                id="esign-mode"
                options={MODES}
                value={draft.mode}
                onChange={(e) => setDraft({ ...draft, mode: e.target.value })}
              />
            </Field>

            <Field
              label="Delay (seconds)"
              htmlFor="esign-delay"
              hint={draft.mode === 'DELAYED' ? undefined : 'only used in DELAYED mode'}
            >
              <TextInput
                id="esign-delay"
                type="number"
                min="0"
                disabled={draft.mode !== 'DELAYED'}
                value={draft.delaySeconds}
                onChange={(e) => setDraft({ ...draft, delaySeconds: e.target.value })}
              />
            </Field>

            <Field
              label="Auto outcome"
              htmlFor="esign-outcome"
              hint={draft.mode === 'SILENT' ? 'not used in SILENT mode' : undefined}
            >
              <Select
                id="esign-outcome"
                options={OUTCOMES}
                disabled={draft.mode === 'SILENT'}
                value={draft.autoOutcome}
                onChange={(e) => setDraft({ ...draft, autoOutcome: e.target.value })}
              />
            </Field>

            <Field
              label="Demo expiry (seconds)"
              htmlFor="esign-expiry"
              hint="blank uses the default expiry window"
            >
              <TextInput
                id="esign-expiry"
                type="number"
                min="1"
                placeholder="default"
                value={draft.demoExpirySeconds}
                onChange={(e) => setDraft({ ...draft, demoExpirySeconds: e.target.value })}
              />
            </Field>
          </FormGrid>

          <FormActions>
            <Button type="submit" variant="primary" busy={saving} busyLabel="Saving…">
              Save
            </Button>
          </FormActions>
        </form>
      )}
    </>
  );
}

function toDraft(config) {
  return {
    mode: config.mode,
    delaySeconds: config.delaySeconds ?? 0,
    autoOutcome: config.autoOutcome,
    demoExpirySeconds: config.demoExpirySeconds ?? '',
  };
}

function fromDraft(draft) {
  return {
    mode: draft.mode,
    delaySeconds: Number(draft.delaySeconds) || 0,
    autoOutcome: draft.autoOutcome,
    demoExpirySeconds: draft.demoExpirySeconds === '' ? null : Number(draft.demoExpirySeconds),
  };
}
