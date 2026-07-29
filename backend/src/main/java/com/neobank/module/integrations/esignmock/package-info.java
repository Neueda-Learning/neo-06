/**
 * <h2>The e-sign provider this module talks to — currently a stand-in for UC 07's mock.</h2>
 *
 * <p>Per {@code integrations.orchestrator}'s own package-info: "your own integrations go beside
 * it, not in it". This is that sibling package for the e-sign provider — a real system in
 * production, a mock control panel once UC 07 (Operate Mock Control Panel) builds one.</p>
 *
 * <h3>Why this is a stub today</h3>
 *
 * <p>UC 07 has not built the mock's HTTP endpoint (or an {@code EsignProviderConfig}-driven
 * behaviour) yet, and UC 04's own brief explicitly allows for that: "若 UC07 的 mock 应用/端点尚未就位，
 * 先在本 UC 内新增一个最小的 EsignMockClient（或等价命名）封装该调用". {@link EsignMockClient} is that
 * minimal seam — {@link InMemoryEsignMockClient} just generates an id, synchronously, with no
 * network call, so every caller (UC 00's initial send, UC 04's resend) already codes against the
 * abstraction and only the implementation needs to change once UC 07 lands a real mock to call.</p>
 */
package com.neobank.module.integrations.esignmock;
