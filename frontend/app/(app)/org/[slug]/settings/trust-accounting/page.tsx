import { notFound } from "next/navigation";
import Link from "next/link";
import {
  ChevronLeft,
  Scale,
  Shield,
  Landmark,
  Bell,
  Plus,
} from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchLpffRates } from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { TrustAccount, LpffRate } from "@/lib/types/trust";

export default async function TrustAccountingSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // Module guard
  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  // Permission guard
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" /> Settings
        </Link>
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

  // Fetch LPFF rates for the primary account
  const primaryAccount =
    trustAccounts.find((a) => a.isPrimary && a.status === "ACTIVE") ??
    trustAccounts[0] ??
    null;

  let lpffRates: LpffRate[] = [];
  if (primaryAccount) {
    try {
      lpffRates = await fetchLpffRates(primaryAccount.id);
    } catch {
      // Non-fatal: show empty state
    }
  }

  return (
    <div className="space-y-8" data-testid="trust-settings-page">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" /> Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Accounting Settings
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage trust accounts, approval settings, LPFF rates, and reminder
          configuration.
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
                <CardDescription>
                  Manage your firm&apos;s trust bank accounts
                </CardDescription>
              </div>
            </div>
            <Link
              href={`/org/${slug}/trust-accounting`}
              className="inline-flex items-center gap-1.5 rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-200"
            >
              <Plus className="size-3.5" />
              Add Account
            </Link>
          </div>
        </CardHeader>
        <CardContent>
          {trustAccounts.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No trust accounts configured. Add a trust account to start
              tracking client funds.
            </p>
          ) : (
            <div className="divide-y divide-slate-200 dark:divide-slate-800">
              {trustAccounts.map((account) => (
                <div
                  key={account.id}
                  className="flex items-center justify-between py-3 first:pt-0 last:pb-0"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-slate-900 dark:text-slate-100">
                        {account.accountName}
                      </p>
                      {account.isPrimary && (
                        <Badge variant="success">Primary</Badge>
                      )}
                      <Badge
                        variant={
                          account.status === "ACTIVE" ? "success" : "neutral"
                        }
                      >
                        {account.status}
                      </Badge>
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      {account.bankName} &middot; {account.branchCode} &middot;{" "}
                      {account.accountNumber}
                    </p>
                  </div>
                  <p className="text-xs text-slate-400">
                    {account.accountType}
                  </p>
                </div>
              ))}
            </div>
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
              <CardDescription>
                Configure transaction approval requirements
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {trustAccounts.length === 0 ? (
              <p className="text-sm text-muted-foreground">
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
                        {account.requireDualApproval
                          ? "Dual approval required"
                          : "Single approval"}
                        {account.paymentApprovalThreshold != null &&
                          ` above R${account.paymentApprovalThreshold.toLocaleString()}`}
                      </p>
                    </div>
                    <Badge
                      variant={
                        account.requireDualApproval ? "warning" : "neutral"
                      }
                    >
                      {account.requireDualApproval ? "Dual" : "Single"}
                    </Badge>
                  </div>
                ))
            )}
          </div>
        </CardContent>
      </Card>

      {/* LPFF Rate Management Section */}
      <Card data-testid="lpff-rates-section">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Landmark className="size-5 text-slate-400" />
            <div>
              <CardTitle>LPFF Rates</CardTitle>
              <CardDescription>
                Law Practice Fidelity Fund interest rate configuration
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {!primaryAccount ? (
            <p className="text-sm text-muted-foreground">
              Add a trust account to manage LPFF rates.
            </p>
          ) : lpffRates.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No LPFF rates configured. Add a rate to start calculating interest
              distributions.
            </p>
          ) : (
            <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                    <th className="px-4 py-2 text-left font-medium text-slate-600 dark:text-slate-400">
                      Effective From
                    </th>
                    <th className="px-4 py-2 text-left font-medium text-slate-600 dark:text-slate-400">
                      Rate
                    </th>
                    <th className="px-4 py-2 text-left font-medium text-slate-600 dark:text-slate-400">
                      LPFF Share
                    </th>
                    <th className="px-4 py-2 text-left font-medium text-slate-600 dark:text-slate-400">
                      Notes
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {lpffRates.map((rate) => (
                    <tr
                      key={rate.id}
                      className="border-b border-slate-200 last:border-0 dark:border-slate-800"
                    >
                      <td className="px-4 py-2 text-slate-900 dark:text-slate-100">
                        {new Date(rate.effectiveFrom).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-2 text-slate-900 dark:text-slate-100">
                        {(rate.ratePercent * 100).toFixed(2)}%
                      </td>
                      <td className="px-4 py-2 text-slate-900 dark:text-slate-100">
                        {(rate.lpffSharePercent * 100).toFixed(2)}%
                      </td>
                      <td className="px-4 py-2 text-slate-500 dark:text-slate-400">
                        {rate.notes ?? "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Reminder Settings Section */}
      <Card data-testid="reminder-settings-section">
        <CardHeader>
          <div className="flex items-center gap-3">
            <Bell className="size-5 text-slate-400" />
            <div>
              <CardTitle>Reminder Settings</CardTitle>
              <CardDescription>
                Configure trust-related notification reminders
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3 text-sm">
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">
                  Low Balance Alert
                </p>
                <p className="text-slate-500 dark:text-slate-400">
                  Notify when a client&apos;s trust balance drops below a
                  threshold
                </p>
              </div>
              <Badge variant="neutral">Configured per account</Badge>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">
                  Reconciliation Due
                </p>
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
            <div className="flex items-center justify-between rounded-lg border border-slate-200 p-4 dark:border-slate-800">
              <div>
                <p className="font-medium text-slate-900 dark:text-slate-100">
                  Interest Run Due
                </p>
                <p className="text-slate-500 dark:text-slate-400">
                  Remind to run monthly interest calculations
                </p>
              </div>
              <Badge variant="neutral">Monthly</Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
