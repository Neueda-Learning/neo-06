import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Field,
  FormActions,
  FormGrid,
  PageHeader,
  Select,
  Spinner,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const MODES = [
  { value: 'INSTANT', label: 'Instant — signs within seconds' },
  { value: 'DELAYED', label: 'Delayed — signs after delaySeconds' },
  { value: 'SILENT', label: 'Silent — never answers, expiry clock decides' },
];

const OUTCOMES = [
  { value: 'SIGN', label: 'Sign' },
  { value: 'DECLINE', label: 'Decline' },
];

/**
 * UC 07 · Operate Mock Control Panel.
 *
 * The whole point (see the brief's AC 6): the dials shown here are what the NEXT envelope plays
 * — changing them never rewrites a case already in flight. This screen always shows the live
 * saved state, not a draft the user is still typing (so the operator can trust what they see is
 * what fires next), and only sends the fields that changed.
 *
 * ⚠️ Compromise disclosed in the backend (`EsignMockService`'s class javadoc): the brief's own
 * build shape is the e-sign mock as its own service/container, proxied by this panel. Here it is
 * embedded in this module's backend instead, because standing up a new container is an infra
 * change this branch is not allowed to make. The dials, the modes and the panel's behaviour are
 * otherwise exactly what the brief describes.
 */
export default function EsignMockPanel() {
  const [config, setConfig] = useState(null);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [draft, setDraft] = useState(null);

  const reload = useCallback(async () => {
    try {
      const current = await api.getEsignConfig();
      setConfig(current);
      setDraft(current);
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  if (error) {
    return (
      <>
        <PageHeader title="Mock control panel" subtitle="UC 07 — e-sign provider dials" />
        <Alert tone="negative">{error}</Alert>
      </>
    );
  }

  if (!draft) {
    return (
      <>
        <PageHeader title="Mock control panel" subtitle="UC 07 — e-sign provider dials" />
        <Spinner />
      </>
    );
  }

  const dirty =
    config &&
    (draft.mode !== config.mode ||
      draft.delaySeconds !== config.delaySeconds ||
      draft.autoOutcome !== config.autoOutcome ||
      (draft.demoExpirySeconds ?? '') !== (config.demoExpirySeconds ?? ''));

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    setSaved(false);
    try {
      const updated = await api.updateEsignConfig({
        mode: draft.mode,
        delaySeconds: Number(draft.delaySeconds),
        autoOutcome: draft.autoOutcome,
        demoExpirySeconds:
          draft.demoExpirySeconds === '' || draft.demoExpirySeconds == null
            ? null
            : Number(draft.demoExpirySeconds),
      });
      setConfig(updated);
      setDraft(updated);
      setError(null);
      setSaved(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Mock control panel"
        subtitle="UC 07 — sign instantly, sign after a delay, stay silent, decline; plus a demo-fast expiry window. Applies to the NEXT envelope only."
      />
      <Card>
        <form onSubmit={save}>
          <FormGrid cols={2}>
            <Field label="Mode" hint="How the mock plays the customer on the NEXT envelope">
              {({ id }) => (
                <Select
                  id={id}
                  options={MODES}
                  value={draft.mode}
                  onChange={(e) => setDraft({ ...draft, mode: e.target.value })}
                />
              )}
            </Field>
            <Field
              label="Auto outcome"
              hint="Which signature event the mock posts in Instant/Delayed modes"
            >
              {({ id }) => (
                <Select
                  id={id}
                  options={OUTCOMES}
                  value={draft.autoOutcome}
                  onChange={(e) => setDraft({ ...draft, autoOutcome: e.target.value })}
                />
              )}
            </Field>
            <Field
              label="Delay (seconds)"
              hint="How long Delayed mode waits before posting its signature event"
            >
              {({ id }) => (
                <TextInput
                  id={id}
                  type="number"
                  min={0}
                  disabled={draft.mode !== 'DELAYED'}
                  value={draft.delaySeconds}
                  onChange={(e) => setDraft({ ...draft, delaySeconds: e.target.value })}
                />
              )}
            </Field>
            <Field
              label="Demo expiry (seconds)"
              hint="Overrides the real expiry window for NEW envelopes only — leave blank for the real default"
            >
              {({ id }) => (
                <TextInput
                  id={id}
                  type="number"
                  min={0}
                  placeholder="use real default"
                  value={draft.demoExpirySeconds ?? ''}
                  onChange={(e) => setDraft({ ...draft, demoExpirySeconds: e.target.value })}
                />
              )}
            </Field>
          </FormGrid>
          <FormActions>
            {saved && !dirty && <Alert tone="positive">Saved — applies to the next envelope</Alert>}
            <Button type="button" variant="ghost" onClick={reload} disabled={saving}>
              Reset
            </Button>
            <Button type="submit" disabled={saving || !dirty}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </FormActions>
        </form>
      </Card>
    </>
  );
}
