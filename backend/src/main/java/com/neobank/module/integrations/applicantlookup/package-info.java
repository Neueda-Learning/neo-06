/**
 * <h2>UC 01 · Name → application ids, resolved by the orchestrator.</h2>
 *
 * <p>This module's schema never stores applicant data — only {@code applicationId} (see
 * {@link com.neobank.module.model.AgreementRecord}). UC 01's "search by name" therefore has to
 * ask the system that <em>does</em> hold the application which ids match a name, then load
 * this module's own rows for those ids. That ask lives here, not in
 * {@code integrations.orchestrator}, because:</p>
 *
 * <ul>
 *   <li>the {@code orchestrator} package is <b>fixed</b> — see its {@code package-info.java}.
 *       It pins the three shapes of the UC 00 conversation (POST in, 202 ack, PUT back) and
 *       nothing else may be added to it;</li>
 *   <li>{@code GET /api/v1/applications?name=} is a v5 contract addition for the search use
 *       case — a different conversation with the same external system, so it gets its own
 *       sibling package, the way {@code package-info.java} in {@code orchestrator} describes:
 *       "your own integrations go beside it, not in it".</li>
 * </ul>
 *
 * <p>One client, one direction: outbound only. The response is a list of application ids —
 * nothing about the applicant is ever brought back into this module, which is the whole point
 * of resolving ids <em>through</em> the orchestrator rather than copying its data.</p>
 *
 * <p>Failures are logged and swallowed (an empty id list is returned): per UC 01 AC7, an
 * orchestrator that is down must never turn a search into a {@code 500} — the id-based path
 * still works, and the UI shows a retryable "—" for names it cannot fetch.</p>
 */
package com.neobank.module.integrations.applicantlookup;
