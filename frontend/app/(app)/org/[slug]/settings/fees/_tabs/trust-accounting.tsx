import { Scale, Shield, Bell } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { Section866Advisory } from "@/components/trust/Section866Advisory";
import { AddTrustAccountButton } from "@/components/trust/AddTrustAccountButton";
import {
  INVESTMENT_BASIS_ACCOUNT_LABELS,
  TRUST_ACCOUNT_SECTION_LABELS,
  type TrustAccount,
} from "@/lib/types/trust";

/**
 * Fees & Trust › Trust accounting tab (Phase 87 S9, ADR-341) — the former trust-accounting settings
 * page's loader. The trust_accounting module gate is the tab's `requiredModule` (resolved by the
 * family seam), so the former page's notFound() module guard is gone: inside a tab it would 404 the
 * whole /settings/fees page (§12.7.4 — never a 404).
 */
export async function renderTrustAccountingTab() {
  // Permission guard (defence in depth — the tab is adminOnly)
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Accounting Settings
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage trust accounting settings.
        </p>
      </div>
    );
  }

  // Fetch trust accounts
  let trustAccounts: TrustAccount[] = [];
  try {
    trustAccounts = await fetchTrustAccounts();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8" data-testid="trust-settings-page">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Accounting Settings
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage trust accounts, approval settings, and reminder configuration.
        </p>
      </div>

      {/* Trust Accounts Section */}
      <Card data-testid="trust-accounts-section">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Scale className="size-5 text-slate-400" />
              <div>
                <CardTitle>Trust Accounts</CardTitle>
                <CardDescription>Manage your firm&apos;s trust bank accounts</CardDescription>
              </div>
            </div>
            <AddTrustAccountButton />
          </div>
        </CardHeader>
        <CardContent>
          {trustAccounts.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              No trust accounts configured. Add a trust account to start tracking client funds.
            </p>
          ) : (
            <>
              <div className="divide-y divide-slate-200 dark:divide-slate-800">
                {trustAccounts.map((account) => (
                  <div
                    key={account.id}
                    className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0"
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-slate-900 dark:text-slate-100">
                          {account.accountName}
                        </p>
                        {account.isPrimary && <Badge variant="success">Primary</Badge>}
                        <Badge variant={account.status === "ACTIVE" ? "success" : "neutral"}>
                          {account.status}
                        </Badge>
                      </div>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {account.bankName} &middot; {account.branchCode} &middot;{" "}
                        {account.accountNumber}
                      </p>
                    </div>
                    {/* ADR-379: the section in the Act's words, and an investment account's basis */}
                    <div className="text-right" data-testid="trust-account-section">
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {TRUST_ACCOUNT_SECTION_LABELS[account.section]}
                      </p>
                      {account.investmentBasis && (
                        <p
                          className="text-xs text-slate-500 dark:text-slate-400"
                          data-testid="trust-account-basis"
                        >
                          {INVESTMENT_BASIS_ACCOUNT_LABELS[account.investmentBasis]}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
              <Section866Advisory className="mt-4" data-testid="section-86-6-settings-advisory" />
            </>
          )}
        </CardContent>
      </Card>

      {/* Approval Settings Section */}
      <Card data-testid="approval-settings-section">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Shield className="size-5 text-slate-400" />
            <div>
              <CardTitle>Approval Settings</CardTitle>
              <CardDescription>Configure transaction approval requirements</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {trustAccounts.length === 0 ? (
              <p className="text-muted-foreground text-sm">
                Add a trust account to configure approval settings.
              </p>
            ) : (
              trustAccounts
                .filter((a) => a.status === "ACTIVE")
                .map((account) => (
                  <div
                    key={account.id}
                    className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800"
                  >
                    <div>
                      <p className="font-medium text-slate-900 dark:text-slate-100">
                        {account.accountName}
                      </p>
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        {account.requireDualApproval ? "Dual approval required" : "Single approval"}
                        {account.paymentApprovalThreshold != null &&
                          ` above ${new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR", minimumFractionDigits: 0 }).format(account.paymentApprovalThreshold)}`}
                      </p>
                    </div>
                    <Badge variant={account.requireDualApproval ? "warning" : "neutral"}>
                      {account.requireDualApproval ? "Dual" : "Single"}
                    </Badge>
                  </div>
                ))
            )}
          </div>
        </CardContent>
      </Card>

      {/* Reminder Settings Section */}
      <Card data-testid="reminder-settings-section">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Bell className="size-5 text-slate-400" />
            <div>
              <CardTitle>Reminder Settings</CardTitle>
              <CardDescription>Configure trust-related notification reminders</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm">
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">Low Balance Alert</p>
                <p className="text-slate-500 dark:text-slate-400">
                  Notify when a client&apos;s trust balance drops below a threshold
                </p>
              </div>
              <Badge variant="neutral">Configured per account</Badge>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">Reconciliation Due</p>
                <p className="text-slate-500 dark:text-slate-400">
                  Remind to complete monthly bank reconciliation
                </p>
              </div>
              <Badge variant="neutral">Monthly</Badge>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">
                  Investment Maturity
                </p>
                <p className="text-slate-500 dark:text-slate-400">
                  Alert before fixed deposits mature
                </p>
              </div>
              <Badge variant="neutral">7 days before</Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
