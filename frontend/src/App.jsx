import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill, TextInput } from './design-system';
import AgreementBoard from './components/AgreementBoard.jsx';
import QueueScreen from './components/QueueScreen.jsx';
import EsignMockPanel from './components/EsignMockPanel.jsx';
import { api } from './api.js';

const HEALTH_MS = 10000;
const OPERATOR_KEY = 'neo06.operator';

/** The screens in the side menu — UC01/02/03/04/05/08's board and queue, plus UC07's dials. */
const SCREENS = [
  { id: 'board', label: 'Agreement board', hint: 'UC 01\u201303\u201305\u201308 \u2014 search, review, act' },
  { id: 'queue', label: 'Pending & expired queue', hint: 'UC 04 \u2014 resend' },
  { id: 'esign-mock', label: 'Mock control panel', hint: 'UC 07 \u2014 e-sign dials' },
];

/**
 * A sidebar rather than a top bar: this app is expected to grow more screens than a row of tabs
 * holds, and the menu is where a team plans that growth. The identity box above it is the only
 * place the app says who it belongs to — its values come from `/info`, so the same image reads
 * "Team 06" once SERVICE_TEAM says so.
 */
export default function App() {
  const [screen, setScreen] = useState('board');
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);
  const [operator, setOperator] = useState(() => localStorage.getItem(OPERATOR_KEY) ?? '');

  useEffect(() => {
    localStorage.setItem(OPERATOR_KEY, operator);
  }, [operator]);

  const refreshHealth = useCallback(async () => {
    try {
      const [h, i] = await Promise.all([api.health(), api.info()]);
      setHealth(h);
      setInfo(i);
      setError(null);
    } catch (e) {
      setHealth(null);
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  const up = !error && health?.status === 'UP';

  return (
    <AppShell
      side={
        <>
          <SideBrand
            brand={info?.team ?? 'Team'}
            product={info?.service ?? 'Module'}
            meta={info ? `${info.serviceId} · ${info.domain}` : undefined}
          />
          <SideNav items={SCREENS} active={screen} onSelect={setScreen} />
          <div className="app-side-status">
            <label className="app-side-operator">
              <span>Operator</span>
              <TextInput
                value={operator}
                onChange={(e) => setOperator(e.target.value)}
                placeholder="your name"
                aria-label="Operator name — recorded on every resend and override"
              />
            </label>
            <StatusPill tone={up ? 'positive' : 'negative'}>{up ? 'Up' : 'Down'}</StatusPill>
            <Button variant="ghost" size="sm" onClick={refreshHealth}>
              Refresh
            </Button>
          </div>
        </>
      }
      footer="One of ten modules · applications arrive from the orchestrator, never from this UI"
    >
      {screen === 'board' && <AgreementBoard operator={operator} />}
      {screen === 'queue' && <QueueScreen operator={operator} />}
      {screen === 'esign-mock' && <EsignMockPanel />}
    </AppShell>
  );
}

