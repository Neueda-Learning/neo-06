// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "GENERATING", "PENDING" and "SIGNED" too.
//
// This is UC00-UC08's AgreementStatus vocabulary — GENERATING/PENDING/SIGNED/DECLINED/EXPIRED —
// not the orchestrator's decision vocabulary (ACCEPTED/REJECTED/REFERRED), which this module
// only ever reports outward, never shows on its own screens.
import { TONES, toneMapper } from './design-system';

export const statusTone = toneMapper({
  GENERATING: TONES.INFO,
  PENDING: TONES.WARNING,
  SIGNED: TONES.POSITIVE,
  DECLINED: TONES.NEGATIVE,
  EXPIRED: TONES.NEGATIVE,
});

/** The statuses the board filters on. GENERATING is internal but still visible to operators. */
export const STATUSES = ['GENERATING', 'PENDING', 'SIGNED', 'DECLINED', 'EXPIRED'];

export function time(iso) {
  return iso ? new Date(iso).toLocaleString() : '—';
}

