import "server-only";

import { createElement, type ReactNode } from "react";
import { Clock, ShieldAlert, ShieldCheck, type LucideIcon } from "lucide-react";
import type { HeroTile } from "@/components/shared/detail-hero";
import { ApiError } from "@/lib/api/client";
import { getClientFicaStatus } from "@/lib/api/clients";
import { getDebtorDetail, type DebtorDetailResponse } from "@/lib/api/collections";
import { listMatterRows } from "@/lib/api/matters";
import { FICA_STATUS_LABEL } from "@/lib/clients-list";
import { hasCapability, type HomeCaps, type WidgetResult } from "@/lib/dashboard-home";
import { formatCurrency, formatDateInSA } from "@/lib/format";
import { overdueHint, tileFrom } from "@/lib/hero-tiles";
import { settle } from "@/lib/settle";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchClientLedger } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import type { FicaOnboardingStatus, FicaStatus, TrustAccount } from "@/lib/types";

// Client hero tiles (Phase 90 642B, arch §15.3.5, ADR-373) — the 641B matter shape. Server-only,
// not a server action: the client page is the only caller and passes its own capabilities.
//   loadClientHeroData   — I/O. Each read once, only under its own gate (the WidgetResult idiom,
//                          lib/settle). The debtor detail is shared with the Chase-history card
//                          (chaseHistory below).
//   buildClientHeroTiles — pure. absent → tile omitted, error → state "error", a real zero → the
//                          formatted zero.
// Tiles cross the RSC boundary into the "use client" ClientHeaderCard, so every field is a string,
// null, or (value / hint only) a pre-rendered element — never a component reference (OQ #26).

const ABSENT = { state: "absent" } as const;

export const CLIENT_HERO_TILE_LABELS = {
  openMatters: "Open matters",
  trust: "Trust balance",
  outstanding: "Outstanding",
  fica: "FICA",
} as const;

export interface ClientTrustBalance {
  /** The primary account's name — the tile hint (OQ #11). */
  accountName: string;
  balance: number;
}

export interface ClientHeroOptions {
  /** trust_accounting enabled (from the page's single /api/settings read). */
  trustEnabled: boolean;
  /** Chase-history activities page (?chasePage=) — the debtor read is shared with that card. */
  chasePage: number;
}

export interface ClientHeroData {
  /**
   * The client's ACTIVE matters the caller can see: `GET /api/matters?customerId=&status=ACTIVE`
   * filters status server-side and by visibility (ADR-346). Not `/api/clients/{id}/matters` — that
   * list carries no status (LinkedMatterResponse).
   */
  openMatterCount: WidgetResult<number>;
  /** absent without INVOICING (admin/owner pass); shared with the Chase-history card. */
  debtor: WidgetResult<DebtorDetailResponse>;
  /** absent when the module is off, without VIEW_TRUST, or when no trust account exists. */
  trustBalance: WidgetResult<ClientTrustBalance>;
  ficaStatus: WidgetResult<FicaStatus>;
}

/** TrustBalanceCard's rule ($FC/trust/TrustBalanceCard.tsx): primary + ACTIVE, else the first. */
function primaryAccount(accounts: TrustAccount[]): TrustAccount | undefined {
  return accounts.find((a) => a.isPrimary && a.status === "ACTIVE") ?? accounts[0];
}

/** null = no trust account. No ledger card for the client = a real zero (backend synthesises one). */
async function readPrimaryLedger(clientId: string): Promise<ClientTrustBalance | null> {
  const account = primaryAccount(await fetchTrustAccounts());
  if (!account) return null;
  try {
    const card = await fetchClientLedger(account.id, clientId);
    return { accountName: account.accountName, balance: card.balance };
  } catch (error) {
    // settle() would turn a 404 into "absent" (tile omitted); here it means "no ledger yet" → 0.
    if (error instanceof ApiError && error.status === 404) {
      return { accountName: account.accountName, balance: 0 };
    }
    throw error;
  }
}

function presentOnly<T>(result: WidgetResult<T | null>): WidgetResult<T> {
  if (result.state !== "ok") return result;
  return result.data === null ? ABSENT : { state: "ok", data: result.data };
}

/** The client page's hero reads (the debtor detail is also the Chase-history card's). */
export async function loadClientHeroData(
  clientId: string,
  caps: HomeCaps,
  { trustEnabled, chasePage }: ClientHeroOptions
): Promise<ClientHeroData> {
  const [openMatterCount, debtor, trust, ficaStatus] = await Promise.all([
    settle(true, "client open matters", async () => {
      const rows = await listMatterRows({ customerId: clientId, status: "ACTIVE" });
      return rows.length;
    }),
    // 591C.2 gate, unchanged: admin ∨ owner ∨ INVOICING (hasCapability is admin/owner-inclusive).
    settle(hasCapability(caps, "INVOICING"), "client debtor detail", () =>
      getDebtorDetail(clientId, { page: chasePage })
    ),
    settle(trustEnabled && hasCapability(caps, "VIEW_TRUST"), "client trust balance", () =>
      readPrimaryLedger(clientId)
    ),
    settle(true, "client FICA status", () => getClientFicaStatus(clientId)),
  ]);
  return { openMatterCount, debtor, trustBalance: presentOnly(trust), ficaStatus };
}

/** The Chase-history card: the debtor detail, or null (hidden) when not fetched / failed (as before). */
export function chaseHistory(data: ClientHeroData): DebtorDetailResponse | null {
  return data.debtor.state === "ok" ? data.debtor.data : null;
}

function exclusionText(excluded: number): string {
  return `Excludes ${excluded} fee ${excluded === 1 ? "note" : "notes"} in other currencies`;
}

/**
 * Default-currency rows only — never a cross-currency sum (rulings §6); the rest are counted. One
 * hint line (compact tile): the exclusion caveat when any row was skipped, else the overdue count.
 */
function outstandingTile(detail: DebtorDetailResponse, currency: string) {
  const rows = detail.outstandingInvoices.filter((row) => row.currency === currency);
  const excluded = detail.outstandingInvoices.length - rows.length;
  const total = rows.reduce((sum, row) => sum + row.balanceDue, 0);
  return {
    value: formatCurrency(total, currency),
    hint:
      excluded > 0
        ? exclusionText(excluded)
        : overdueHint(rows.filter((row) => row.daysOverdue > 0).length),
  };
}

const FICA_ICON: Record<FicaOnboardingStatus, { icon: LucideIcon; className: string }> = {
  DONE: { icon: ShieldCheck, className: "text-success" },
  IN_PROGRESS: { icon: Clock, className: "text-warning" },
  NOT_STARTED: { icon: ShieldAlert, className: "text-danger" },
};

/** Label + icon, never colour alone (ADR-373 / ADR-370); the icon is decorative. */
function ficaTile(fica: FicaStatus): { value: ReactNode; hint?: ReactNode } {
  const status: FicaOnboardingStatus = fica.status in FICA_ICON ? fica.status : "NOT_STARTED";
  const { icon, className } = FICA_ICON[status];
  return {
    value: createElement(
      "span",
      { className: "inline-flex items-center gap-1.5 font-sans" },
      createElement(icon, { "aria-hidden": true, className: `size-4 shrink-0 ${className}` }),
      FICA_STATUS_LABEL[status]
    ),
    // FicaStatusCard's wording (ADR-348: a checklist decision is "Completed"); SA calendar.
    hint:
      fica.status === "DONE" && fica.lastVerifiedAt
        ? `${fica.checklistInstanceId ? "Completed" : "Verified"} ${formatDateInSA(fica.lastVerifiedAt)}`
        : undefined,
  };
}

/**
 * Pure: Open matters, Trust balance, Outstanding, FICA — gated tiles omitted. `defaultCurrency`
 * null = the settings read failed: Outstanding cannot apply the currency rule → state "error".
 */
export function buildClientHeroTiles(
  data: ClientHeroData,
  defaultCurrency: string | null
): HeroTile[] {
  const L = CLIENT_HERO_TILE_LABELS;
  const debtor: WidgetResult<DebtorDetailResponse> =
    defaultCurrency === null && data.debtor.state === "ok" ? { state: "error" } : data.debtor;
  return [
    tileFrom("openMatters", L.openMatters, data.openMatterCount, (count) => ({
      value: String(count),
    })),
    tileFrom("trust", L.trust, data.trustBalance, (trust) => ({
      value: formatCurrency(trust.balance, defaultCurrency ?? undefined),
      hint: trust.accountName,
    })),
    tileFrom("outstanding", L.outstanding, debtor, (detail) =>
      outstandingTile(detail, defaultCurrency as string)
    ),
    tileFrom("fica", L.fica, data.ficaStatus, ficaTile),
  ].filter((tile): tile is HeroTile => tile !== null);
}
