/**
 * <h2>The e-sign provider this module talks to — an in-process mock, with UC 07's dials.</h2>
 *
 * <p>Per {@code integrations.orchestrator}'s own package-info: "your own integrations go beside
 * it, not in it". This is that sibling package for the e-sign provider — a real system in
 * production, an in-process mock here, driven by the dials
 * {@link EsignProviderConfigService}/{@link EsignProviderConfig} hold and
 * {@link com.neobank.module.controller.EsignConfigController} exposes to the operator panel.</p>
 *
 * <h3>Why "in-process" rather than a second container</h3>
 *
 * <p>The doc's own Build notes describe the mock as "its own service and container", the way the
 * orchestrator sidecar is. This module took a lighter path instead: {@link EsignMockClient} is the
 * seam a real, separate provider would sit behind, but {@link InMemoryEsignMockClient} — the only
 * implementation — plays the customer with a direct, in-process call into
 * {@link com.neobank.module.service.SignatureEventService} rather than a second HTTP hop back into
 * this same backend. The observable effect on {@link com.neobank.module.model.AgreementRecord} is
 * identical either way; what changes is only how "the mock posts to {@code /signature-events}"
 * happens under the hood.</p>
 */
package com.neobank.module.integrations.esignmock;
