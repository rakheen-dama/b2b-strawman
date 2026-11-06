"use client";

import { useCallback, useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { ModuleGate } from "@/components/module-gate";
import { useOrgProfile } from "@/lib/org-profile";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import {
  Scale,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  Loader2,
  AlertTriangle,
} from "lucide-react";
import { RecordDepositDialog } from "@/components/trust/RecordDepositDialog";
import { RecordPaymentDialog } from "@/components/trust/RecordPaymentDialog";
import { RecordFeeTransferDialog } from "@/components/trust/RecordFeeTransferDialog";
import { LedgerTotalsBreakdown } from "@/components/trust/ledger-totals-breakdown";
import {
  fetchMatterTrustBalance,
  fetchTrustAccounts,
} from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchClientLedger } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import type { WidgetResult } from "@/lib/dashboard-home";
import type {
  MatterTrustBalance,
  TrustAccount,
  ClientLedgerCard as ClientLedgerCardType,
} from "@/lib/types";

interface TrustBalanceCardProps {
  customerId: string;
  trustAccountId?: string;
  projectId?: string;
  slug: string;
  showQuickActions?: boolean;
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
    minimumFractionDigits: 2,
  }).format(amount);
}

export function TrustBalanceCard({
  customerId,
  trustAccountId,
  projectId,
  slug,
  showQuickActions = false,
}: TrustBalanceCardProps) {
  const { isModuleEnabled } = useOrgProfile();
  const moduleEnabled = isModuleEnabled("trust_accounting");
  const [depositOpen, setDepositOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [feeTransferOpen, setFeeTransferOpen] = useState(false);
  const { mutate: globalMutate } = useSWRConfig();

  // Fetch trust accounts to find primary if not provided
  // Gate on module being enabled to avoid 403/500 errors for tenants without trust_accounting
  const accountsCacheKey = moduleEnabled && !trustAccountId ? `trust-accounts-${slug}` : null;
  const { data: accounts, isLoading: accountsLoading } = useSWR<TrustAccount[]>(
    accountsCacheKey,
    () => fetchTrustAccounts()
  );

  const resolvedAccountId =
    trustAccountId ??
    accounts?.find((a) => a.isPrimary && a.status === "ACTIVE")?.id ??
    accounts?.[0]?.id;

  // Fetch client ledger for this customer
  // Gate on moduleEnabled to avoid 403/500 for tenants without trust_accounting
  const ledgerCacheKey =
    moduleEnabled && resolvedAccountId
      ? `trust-ledger-${slug}-${resolvedAccountId}-${customerId}`
      : null;
  const {
    data: ledger,
    isLoading: ledgerLoading,
    error: ledgerError,
  } = useSWR<ClientLedgerCardType>(ledgerCacheKey, () =>
    fetchClientLedger(resolvedAccountId!, customerId)
  );

  // Matter mode (G89-F14): on a matter's Trust tab the primary figure is the matter's own
  // sub-ledger — the same source as the closure gate and the Overview tile — not the client card.
  const isMatterMode = Boolean(projectId);
  const matterCacheKey =
    moduleEnabled && projectId ? `matter-trust-balance-${slug}-${projectId}` : null;
  const {
    data: matterResult,
    isLoading: matterLoading,
    error: matterFetchError,
  } = useSWR<WidgetResult<MatterTrustBalance>>(matterCacheKey, () =>
    fetchMatterTrustBalance(projectId!)
  );
  const matterError =
    isMatterMode && (Boolean(matterFetchError) || (matterResult && matterResult.state !== "ok"));

  // The figure the card is about: the matter balance on a matter, the client card otherwise
  const primaryBalance = isMatterMode
    ? matterResult?.state === "ok"
      ? matterResult.data.balance
      : undefined
    : ledger?.balance;

  const isLoading = accountsLoading || ledgerLoading || matterLoading;
  const loadError = Boolean(ledgerError) || matterError;

  // Revalidate SWR caches after a successful transaction mutation
  const handleMutationSuccess = useCallback(() => {
    if (ledgerCacheKey) globalMutate(ledgerCacheKey);
    if (accountsCacheKey) globalMutate(accountsCacheKey);
    if (matterCacheKey) globalMutate(matterCacheKey);
  }, [globalMutate, ledgerCacheKey, accountsCacheKey, matterCacheKey]);

  return (
    <ModuleGate module="trust_accounting">
      <Card data-testid="trust-balance-card">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Scale className="size-5 text-slate-400" />
            <CardTitle>Trust Balance</CardTitle>
            {primaryBalance !== undefined && primaryBalance > 0 && (
              <Badge variant="success">Funds Held</Badge>
            )}
            {primaryBalance === 0 && <Badge variant="neutral">No Funds</Badge>}
            {primaryBalance !== undefined && primaryBalance < 0 && (
              <Badge variant="destructive">Overdrawn</Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {isLoading && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="size-4 animate-spin" />
              Loading trust balance...
            </div>
          )}

          {!isLoading && loadError && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <AlertTriangle className="size-4 text-slate-400" />
              Unable to load trust balance
            </div>
          )}

          {!isLoading && !loadError && !resolvedAccountId && (
            <p className="text-muted-foreground text-sm">
              No trust account configured. Set up a trust account in settings to track client funds.
            </p>
          )}

          {!isLoading && !loadError && resolvedAccountId && primaryBalance === undefined && (
            <p className="text-muted-foreground text-sm">
              No trust transactions recorded for this client yet.
            </p>
          )}

          {!isLoading && !loadError && resolvedAccountId && primaryBalance !== undefined && (
            <div className="space-y-4">
              <div
                className="text-3xl font-semibold text-slate-950 dark:text-slate-50"
                data-testid="trust-balance-primary"
              >
                {formatCurrency(primaryBalance)}
              </div>

              {isMatterMode && ledger && (
                <p
                  className="text-sm text-slate-500 dark:text-slate-400"
                  data-testid="trust-balance-client-line"
                >
                  {ledger.clientName} — trust balance across all matters:{" "}
                  <span className="font-medium text-slate-700 tabular-nums dark:text-slate-300">
                    {formatCurrency(ledger.balance)}
                  </span>
                </p>
              )}

              {/* The posted totals that make up the client balance (G89-F13); client mode only */}
              {!isMatterMode && ledger && (
                <>
                  <LedgerTotalsBreakdown ledger={ledger} />
                  {ledger.lastTransactionDate && (
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                      Last transaction:{" "}
                      {new Date(ledger.lastTransactionDate).toLocaleDateString("en-ZA")}
                    </p>
                  )}
                </>
              )}

              {showQuickActions && resolvedAccountId && ledger && (
                <div className="flex flex-wrap gap-2 border-t border-slate-200 pt-2 dark:border-slate-800">
                  <Button variant="outline" size="sm" onClick={() => setDepositOpen(true)}>
                    <ArrowDownLeft className="mr-1.5 size-3.5" />
                    Record Deposit
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setPaymentOpen(true)}>
                    <ArrowUpRight className="mr-1.5 size-3.5" />
                    Record Payment
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setFeeTransferOpen(true)}>
                    <ArrowLeftRight className="mr-1.5 size-3.5" />
                    Fee Transfer
                  </Button>

                  <RecordDepositDialog
                    accountId={resolvedAccountId}
                    slug={slug}
                    open={depositOpen}
                    onOpenChange={setDepositOpen}
                    onSuccess={handleMutationSuccess}
                    customers={[{ id: customerId, name: ledger.clientName, email: "" }]}
                    defaultCustomerId={customerId}
                    defaultProjectId={projectId}
                  />
                  <RecordPaymentDialog
                    accountId={resolvedAccountId}
                    open={paymentOpen}
                    onOpenChange={setPaymentOpen}
                    onSuccess={handleMutationSuccess}
                    customers={[{ id: customerId, name: ledger.clientName, email: "" }]}
                    defaultCustomerId={customerId}
                    defaultProjectId={projectId}
                  />
                  <RecordFeeTransferDialog
                    accountId={resolvedAccountId}
                    open={feeTransferOpen}
                    onOpenChange={setFeeTransferOpen}
                    onSuccess={handleMutationSuccess}
                  />
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </ModuleGate>
  );
}
