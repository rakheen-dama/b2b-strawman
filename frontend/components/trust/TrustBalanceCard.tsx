"use client";

import { useState } from "react";
import useSWR from "swr";
import { ModuleGate } from "@/components/module-gate";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
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
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchClientLedger } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";
import type { TrustAccount, ClientLedgerCard as ClientLedgerCardType } from "@/lib/types";

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
  projectId: _projectId,
  slug,
  showQuickActions = false,
}: TrustBalanceCardProps) {
  // _projectId reserved for future per-project transaction filtering
  void _projectId;
  const [depositOpen, setDepositOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [feeTransferOpen, setFeeTransferOpen] = useState(false);

  // Fetch trust accounts to find primary if not provided
  const { data: accounts, isLoading: accountsLoading } = useSWR<TrustAccount[]>(
    !trustAccountId ? "trust-accounts" : null,
    () => fetchTrustAccounts(),
  );

  const resolvedAccountId =
    trustAccountId ?? accounts?.find((a) => a.isPrimary && a.status === "ACTIVE")?.id ?? accounts?.[0]?.id;

  // Fetch client ledger for this customer
  const { data: ledger, isLoading: ledgerLoading, error: ledgerError } = useSWR<ClientLedgerCardType>(
    resolvedAccountId ? `trust-ledger-${resolvedAccountId}-${customerId}` : null,
    () => fetchClientLedger(resolvedAccountId!, customerId),
  );

  const isLoading = accountsLoading || ledgerLoading;

  return (
    <ModuleGate module="trust_accounting">
      <Card data-testid="trust-balance-card">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Scale className="size-5 text-slate-400" />
            <CardTitle>Trust Balance</CardTitle>
            {ledger && ledger.balance > 0 && (
              <Badge variant="success">Funds Held</Badge>
            )}
            {ledger && ledger.balance === 0 && (
              <Badge variant="neutral">No Funds</Badge>
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

          {ledgerError && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <AlertTriangle className="size-4 text-slate-400" />
              Unable to load trust balance
            </div>
          )}

          {!isLoading && !ledgerError && !resolvedAccountId && (
            <p className="text-sm text-muted-foreground">
              No trust account configured. Set up a trust account in settings to track client funds.
            </p>
          )}

          {!isLoading && !ledgerError && resolvedAccountId && !ledger && (
            <p className="text-sm text-muted-foreground">
              No trust transactions recorded for this client yet.
            </p>
          )}

          {!isLoading && !ledgerError && ledger && (
            <div className="space-y-4">
              <div className="text-3xl font-semibold text-slate-950 dark:text-slate-50">
                {formatCurrency(ledger.balance)}
              </div>

              <div className="grid grid-cols-3 gap-4 text-sm">
                <div>
                  <p className="text-slate-500 dark:text-slate-400">Deposits</p>
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrency(ledger.totalDeposits)}
                  </p>
                </div>
                <div>
                  <p className="text-slate-500 dark:text-slate-400">Payments</p>
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrency(ledger.totalPayments)}
                  </p>
                </div>
                <div>
                  <p className="text-slate-500 dark:text-slate-400">Fee Transfers</p>
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    {formatCurrency(ledger.totalFeeTransfers)}
                  </p>
                </div>
              </div>

              {ledger.lastTransactionDate && (
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  Last transaction: {new Date(ledger.lastTransactionDate).toLocaleDateString()}
                </p>
              )}

              {showQuickActions && resolvedAccountId && (
                <div className="flex flex-wrap gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setDepositOpen(true)}
                  >
                    <ArrowDownLeft className="mr-1.5 size-3.5" />
                    Record Deposit
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPaymentOpen(true)}
                  >
                    <ArrowUpRight className="mr-1.5 size-3.5" />
                    Record Payment
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setFeeTransferOpen(true)}
                  >
                    <ArrowLeftRight className="mr-1.5 size-3.5" />
                    Fee Transfer
                  </Button>

                  <RecordDepositDialog
                    accountId={resolvedAccountId}
                    slug={slug}
                    open={depositOpen}
                    onOpenChange={setDepositOpen}
                  />
                  <RecordPaymentDialog
                    accountId={resolvedAccountId}
                    open={paymentOpen}
                    onOpenChange={setPaymentOpen}
                  />
                  <RecordFeeTransferDialog
                    accountId={resolvedAccountId}
                    open={feeTransferOpen}
                    onOpenChange={setFeeTransferOpen}
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
