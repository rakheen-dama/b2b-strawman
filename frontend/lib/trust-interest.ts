/**
 * Section 86(4) interest split (648B, ADR-379): the Legal Practitioners' Fidelity Fund's 5% of the
 * gross, rounded half-up to the cent, and the client's 95% (gross − Fund share). Integer cents
 * throughout — the server (`TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT`) is the
 * authority; these figures only show the firm what will be posted before it presses Record.
 */

/** 5% — mirrors `TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT`. */
const FUND_SHARE_PERCENT = 5;

/** R0,01 — mirrors `STATUTORY_LPFF_SHARE_TOLERANCE` (the bank's own rounding, OL-5). */
export const FUND_SHARE_TOLERANCE_CENTS = 1;

/** Rand to integer cents (tolerant of float error, e.g. 0.1 + 0.2). */
export const toCents = (rand: number) => Math.round(rand * 100);

/** 5% of `grossCents`, half-up to the cent. */
export const fundShareCents = (grossCents: number) =>
  Math.floor((grossCents * FUND_SHARE_PERCENT + 50) / 100);

/**
 * The split of a gross in rand: the Fund's share (the typed one when given, else 5%) and the
 * client's share. Null when the gross is not a positive amount.
 */
export function interestSplit(
  gross: number | undefined,
  typedFundShare?: number
): { fundShare: number; clientShare: number } | null {
  if (gross === undefined || Number.isNaN(gross) || gross <= 0) {
    return null;
  }
  const grossCents = toCents(gross);
  const fundCents =
    typedFundShare === undefined || Number.isNaN(typedFundShare)
      ? fundShareCents(grossCents)
      : toCents(typedFundShare);
  return { fundShare: fundCents / 100, clientShare: (grossCents - fundCents) / 100 };
}

/** True when a typed Fund share is within R0,01 of 5% of the gross (the server's rule). */
export const fundShareWithinTolerance = (gross: number, typedFundShare: number) =>
  Math.abs(toCents(typedFundShare) - fundShareCents(toCents(gross))) <= FUND_SHARE_TOLERANCE_CENTS;

/** The server's sentence for a typed share outside the tolerance, e.g. "(R20,00)". */
export const fundShareToleranceMessage = (gross: number) =>
  `The Fund's share must be within R0,01 of 5% of the gross interest (R${(
    fundShareCents(toCents(gross)) / 100
  )
    .toFixed(2)
    .replace(".", ",")}).`;
