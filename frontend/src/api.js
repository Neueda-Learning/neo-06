// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-06) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

// This UI reads the case data UC00-UC06 build, and writes only the two operator actions UC04
// and UC08 grant it (resend, override) — plus UC07's own mock dials. Applications themselves
// still only ever arrive from the orchestrator; nothing here creates one.
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: () => request('/api/v1/applications'),
  getApplication: (id) => request(`/api/v1/applications/${id}`),

  // UC01 — Search Cases.
  searchCases: (q, status, limit = 10) => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (status) params.set('status', status);
    params.set('limit', limit);
    return request(`/cases?${params.toString()}`);
  },
  // UC02 — Review Agreement.
  getCase: (id) => request(`/cases/${id}`),
  // UC03 — View Applicant (proxied through this module, never persisted).
  getApplicant: (id) => request(`/cases/${id}/applicant`),
  // UC05 — the generated agreement PDF. A URL, not a fetch: screens open/embed it directly.
  documentUrl: (id) => `${BASE}/cases/${id}/document`,
  // UC04 — Pending & Expired Queue, and the one write it grants operators.
  getQueue: (state, limit = 10) => request(`/queue?state=${state}&limit=${limit}`),
  resendCase: (id, operator) =>
    request(`/cases/${id}/resend`, { method: 'POST', body: JSON.stringify({ operator }) }),
  // UC08 — Override Case.
  overrideCase: (id, { newStatus, reason, operator }) =>
    request(`/cases/${id}/override`, {
      method: 'POST',
      body: JSON.stringify({ newStatus, reason, operator }),
    }),

  // UC 07 — the one screen in this module that IS allowed to write freely: the e-sign mock's own
  // admin dials, not application data.
  getEsignConfig: () => request('/esign/config'),
  updateEsignConfig: (update) =>
    request('/esign/config', { method: 'PUT', body: JSON.stringify(update) }),
};

