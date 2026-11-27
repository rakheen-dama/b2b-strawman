import { notFound } from "next/navigation";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { listClients } from "@/lib/api/clients";
import { getOrgSettings } from "@/lib/api/settings";
import type { InvoiceResponse, InvoiceStatus, OutstandingSummary } from "@/lib/types";
import { hasCapability, type WidgetResult } from "@/lib/dashboard-home";
import { invoiceStatusLabel } from "@/components/invoices/status-badge";
import { CreateInvoiceButton } from "@/components/invoices/create-invoice-button";
import { FeeNotesSubNav } from "@/components/invoices/fee-notes-sub-nav";
import { FeeNoteStatusFilter } from "@/components/invoices/fee-note-status-filter";
import { FeeNotesTable } from "@/components/invoices/fee-notes-table";
import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";
import { formatCurrency } from "@/lib/format";
import { Receipt } from "lucide-react";
import { HelpTip } from "@/components/help-tip";
import { fetchOutstandingSummaryAction } from "./invoice-crud-actions";
import { isoDateInSA, todayInSA } from "@b2mash/shared/date";

/**
 * "Paid This Month" — the one tile still computed from the list. The debtor-book tiles read
 * GET /api/invoices/outstanding-summary (Phase 88 U3, ADR-347), never a client-side sum.
 */
function computePaidThisMonth(invoices: InvoiceResponse[], fallbackCurrency: string) {
  // "This month" is the South African calendar month; paidAt instants are compared by their SA
  // date (G88-F49 — the SSR server runs in UTC).
  const monthStart = `${todayInSA().slice(0, 8)}01`;

  // Use the first invoice's currency as the default, falling back to the org's
  // configured default currency (ZAR for legal-za tenants) instead of USD.
  let paidCurrency = invoices.length > 0 ? invoices[0].currency : fallbackCurrency;
  let paidThisMonth = 0;

  for (const inv of invoices) {
    if (inv.status === "PAID" && inv.paidAt) {
      if (isoDateInSA(new Date(inv.paidAt)) >= monthStart) {
        paidThisMonth += inv.total;
        paidCurrency = inv.currency;
      }
    }
  }

  return { paidThisMonth, paidCurrency };
}

export default async function InvoicesPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string; customerId?: string }>;
}) {
  const { slug } = await params;
  const search = await searchParams;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("INVOICING")) {
    notFound();
  }

  // The debtor-book summary needs FINANCIAL_VISIBILITY (admin/owner inherit it); the page only
  // needs INVOICING. Without it the tiles are absent — no request, no guaranteed 403.
  const summaryRead: Promise<WidgetResult<OutstandingSummary>> = hasCapability(
    capData,
    "FINANCIAL_VISIBILITY"
  )
    ? fetchOutstandingSummaryAction()
    : Promise.resolve({ state: "absent" });

  // All four reads run concurrently. Each list read swallows its own failure; the summary never
  // rejects either (it settles to absent/error) except on an expired session → re-login.
  const [summaryResult, invoices, customers, fallbackCurrency] = await Promise.all([
    summaryRead,
    (async (): Promise<InvoiceResponse[]> => {
      try {
        const queryParams = new URLSearchParams();
        if (search.status) queryParams.set("status", search.status);
        if (search.customerId) queryParams.set("clientId", search.customerId);
        const qs = queryParams.toString();
        return await api.get<InvoiceResponse[]>(`/api/invoices${qs ? `?${qs}` : ""}`);
      } catch {
        // Non-fatal: show empty state
        return [];
      }
    })(),
    // Active clients for the "New fee note" button
    (async (): Promise<{ id: string; name: string }[]> => {
      try {
        const raw = await listClients();
        return (Array.isArray(raw) ? raw : [])
          .filter((c) => c.status === "ACTIVE")
          .map((c) => ({ id: c.id, name: c.name }));
      } catch {
        // Non-fatal — button will be hidden if no customers
        return [];
      }
    })(),
    // The org's configured default currency, so empty-state KPIs display using the tenant's
    // currency (e.g. ZAR) rather than a hardcoded USD fallback.
    (async (): Promise<string> => {
      try {
        const orgSettings = await getOrgSettings();
        return orgSettings.defaultCurrency ?? "USD";
      } catch {
        // Non-fatal — fall through to USD.
        return "USD";
      }
    })(),
  ]);

  // "Paid This Month" from the listed fee notes (when no filter applied, or from filtered set)
  const paid = computePaidThisMonth(invoices, fallbackCurrency);
  // The three debtor-book tiles: present (ok/error) or absent, never zero-filled (§18.9).
  const summary = summaryResult.state === "ok" ? summaryResult.data : null;
  const summaryState = summary ? "ready" : "error";
  const summaryMoney = (pick: (data: OutstandingSummary) => number) =>
    summary ? formatCurrency(pick(summary), summary.currency) : null;

  const showSummaryTiles = summaryResult.state !== "absent";

  const { t } = createMessages("empty-states");

  const statusOptions: InvoiceStatus[] = [
    "DRAFT",
    "APPROVED",
    "SENT",
    "PARTIALLY_PAID",
    "PAID",
    "VOID",
  ];

  // Validate search.status against the whitelist — reject unknown values
  const validatedStatus = statusOptions.includes(search.status as InvoiceStatus)
    ? (search.status as InvoiceStatus)
    : undefined;

  return (
    <div className="space-y-8">
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Fee notes
            <HelpTip code="invoices.lifecycle" />
          </span>
        }
        actions={
          <>
            {invoices.length > 0 && (
              <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                {invoices.length}
              </span>
            )}
            <CreateInvoiceButton customers={customers} slug={slug} />
          </>
        }
      />

      <FeeNotesSubNav slug={slug} current="fee-notes" />

      {/* Summary tiles — debtor-book tiles from GET /api/invoices/outstanding-summary (ADR-347).
          Without them "Paid This Month" stands alone at a card's width, not a quarter of a row.
          Container-query columns: the page column is ~712px at 1024 (2-up) and ~1128px at 1440
          (4-up); one column under 28rem so an 11-character value never spills its card. */}
      <div className="@container">
        <div
          className={
            showSummaryTiles
              ? "grid grid-cols-1 gap-4 @md:grid-cols-2 @3xl:grid-cols-4"
              : "max-w-xs"
          }
        >
          {showSummaryTiles && (
            <>
              <StatCard
                label="Total outstanding"
                data-testid="fee-notes-total-outstanding"
                state={summaryState}
                value={summaryMoney((s) => s.outstandingTotal)}
              />
              <StatCard
                label="Overdue"
                data-testid="fee-notes-overdue"
                state={summaryState}
                value={
                  summary ? (
                    // K-73: red only when something is overdue.
                    <span
                      className={summary.overdueTotal > 0 ? "text-danger-foreground" : undefined}
                    >
                      {formatCurrency(summary.overdueTotal, summary.currency)}
                    </span>
                  ) : null
                }
              />
              <StatCard
                label="Approved, not sent"
                data-testid="fee-notes-approved-unsent"
                state={summaryState}
                value={summaryMoney((s) => s.approvedUnsentTotal)}
              />
            </>
          )}
          <StatCard
            label="Paid This Month"
            data-testid="fee-notes-paid-this-month"
            value={
              <span className="text-success-foreground">
                {formatCurrency(paid.paidThisMonth, paid.paidCurrency)}
              </span>
            }
          />
        </div>
      </div>

      <FeeNotesTable
        slug={slug}
        invoices={invoices}
        toolbar={{
          filters: (
            <FeeNoteStatusFilter
              slug={slug}
              statusOptions={statusOptions}
              activeStatus={validatedStatus}
            />
          ),
        }}
        empty={
          <EmptyState
            variant="default"
            icon={Receipt}
            title={
              validatedStatus
                ? `No ${invoiceStatusLabel(validatedStatus).toLowerCase()} fee notes found`
                : t("invoices.list.heading")
            }
            description={
              validatedStatus
                ? `No ${invoiceStatusLabel(validatedStatus).toLowerCase()} fee notes found.`
                : t("invoices.list.description")
            }
          />
        }
      />
    </div>
  );
}
