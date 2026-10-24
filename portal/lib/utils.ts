// `cn` lives in `@b2mash/ui` (Wave 2.4) — the single source of truth for the
// clsx + tailwind-merge class combiner. Re-export it here so existing
// `@/lib/utils` import sites keep working without mass rewrites. The
// portal-specific helpers below stay put — they are not shared.
export { cn } from "@b2mash/ui/cn";

import { formatCurrency } from "@b2mash/shared/format";
import type { PortalInvoice } from "@/lib/types";

/** Returns true if `url` uses http: or https: protocol. */
export function isSafeImageUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

const HEX_COLOR_RE = /^#[0-9a-fA-F]{3,8}$/;

/** Returns true if `color` is a valid hex color string (#RGB, #RRGGBB, #RRGGBBAA). */
export function isValidHexColor(color: string): boolean {
  return HEX_COLOR_RE.test(color);
}

/**
 * Readable fallback label for a status enum the badge label maps do not know yet:
 * "SOME_STATUS" → "Some status" (sentence case, matching the mapped labels).
 */
export function humaniseStatus(status: string): string {
  const text = status.replace(/_/g, " ").toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

type PartPaymentFields = Pick<
  PortalInvoice,
  "status" | "amountPaid" | "balanceDue" | "currency"
>;

/**
 * True when a fee note is PARTIALLY_PAID and its paid amount is known (ADR-347).
 * Pre-V2 rows read amountPaid 0 until their next sync (624A) — show no amounts then (R-4).
 */
export function hasKnownPartPayment(
  invoice: Pick<PortalInvoice, "status" | "amountPaid">,
): boolean {
  return invoice.status === "PARTIALLY_PAID" && invoice.amountPaid > 0;
}

/**
 * Client copy for paying the rest of a PARTIALLY_PAID fee note. The portal never offers
 * Pay Now on a part-paid note (G88-M11 portal half, R-3), so the client contacts the firm.
 */
export function partPaymentContactMessage(
  invoice: PartPaymentFields,
  orgName: string,
): string {
  return hasKnownPartPayment(invoice)
    ? `To pay the balance of ${formatCurrency(invoice.balanceDue, invoice.currency)}, contact ${orgName}.`
    : `A part payment has been received. Contact ${orgName} for the outstanding balance.`;
}
