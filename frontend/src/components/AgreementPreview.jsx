import React from 'react';
import { time } from '../status.js';

/**
 * A read-only, on-screen mock of the stored PDF's letterhead layout — UC05's document, rendered
 * from the same terms {@link CaseDetail} already fetched (this module's own {@code /cases/{id}}
 * and {@code /cases/{id}/applicant}), not a second copy of the template. The actual legal artifact
 * stays the PDF byte-identically stored in {@code OfferDocument}; this is a preview of it, so an
 * operator can see the agreement's shape without opening a new tab.
 */
export default function AgreementPreview({ detail, applicant }) {
  return (
    <div className="agreement-preview" aria-label="Agreement document preview">
      <div className="agreement-preview__band">
        <span className="agreement-preview__brand">NEO BANK</span>
        <span className="agreement-preview__title">Credit Agreement</span>
      </div>

      <div className="agreement-preview__body">
        <p className="agreement-preview__ref">Reference: {detail.reference ?? '—'}</p>

        <section className="agreement-preview__section">
          <h5>Applicant</h5>
          <hr />
          <dl>
            <dt>Name</dt>
            <dd>{applicant?.fullName ?? '—'}</dd>
          </dl>
        </section>

        <section className="agreement-preview__section">
          <h5>Agreement terms</h5>
          <hr />
          <dl>
            <dt>Product</dt>
            <dd>{applicant?.productCode ?? '—'}</dd>
            <dt>Approved credit limit</dt>
            <dd>{detail.approvedLimit != null ? `£${detail.approvedLimit}` : '—'}</dd>
            <dt>APR</dt>
            <dd>{detail.apr != null ? `${detail.apr}%` : '—'}</dd>
            <dt>Minimum monthly payment</dt>
            <dd>{detail.minPaymentGbp != null ? `£${detail.minPaymentGbp}` : '—'}</dd>
            <dt>Terms version</dt>
            <dd>{detail.termsVersion ?? '—'}</dd>
          </dl>
        </section>

        <p className="agreement-preview__boilerplate">
          This Credit Agreement is made between NEO Bank plc (&quot;the Bank&quot;) and the
          Applicant named above for the product and the credit limit stated above. By signing
          below, or by accepting electronically through the Bank&apos;s e-signature service, the
          Applicant agrees to repay all sums drawn under this agreement together with interest at
          the APR stated above, and to pay at least the minimum monthly payment stated above by
          each due date, in accordance with the terms and conditions identified by the Terms
          version above.
        </p>

        <div className="agreement-preview__signature">
          <div>
            <div className="agreement-preview__sig-line" />
            <span>Applicant signature</span>
          </div>
          <div>
            <div className="agreement-preview__sig-line" />
            <span>Date</span>
          </div>
        </div>

        <p className="agreement-preview__footer">
          System-generated agreement preview — generated {time(detail.sentAt)}
        </p>
      </div>
    </div>
  );
}
