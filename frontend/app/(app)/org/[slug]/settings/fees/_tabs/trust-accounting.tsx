import { Scale, Shield, Bell } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { Badge } from "@b2mash/ui/badge";
import { SettingsSection } from "@/components/settings/settings-section";
import { Section866Advisory } from "@/components/trust/Section866Advisory";
import { AddTrustAccountButton } from "@/components/trust/AddTrustAccountButton";
import {
  INVESTMENT_BASIS_ACCOUNT_LABELS,
  TRUST_ACCOUNT_SECTION_LABELS,
  type TrustAccount,
} from "@/lib/types/trust";

/** K-124 (founder-approved copy): the account status in words. `CLOSED` keeps today's text (DA-6). */
function trustAccountStatusLabel(status: TrustAccount["status"]): string {
  return status === "ACTIVE" ? "Active" : status;
}

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
        <PageHeader title="Trust accounting" />
        <p className="text-muted-foreground">
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
      <PageHeader
        title="Trust accounting"
        description="Manage trust accounts, approval settings, and reminder configuration."
      />

      {/* Trust Accounts Section */}
      <SettingsSection
        data-testid="trust-accounts-section"
        title={
          <span className="inline-flex items-center gap-3">
            <Scale className="text-muted-foreground size-5" />
            Trust Accounts
          </span>
        }
        description="Manage your firm's trust bank accounts"
        actions={<AddTrustAccountButton />}
      >
        {trustAccounts.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            No trust accounts configured. Add a trust account to start tracking client funds.
          </p>
        ) : (
          <>
            <div className="divide-border divide-y">
              {trustAccounts.map((account) => (
                <div
                  key={account.id}
                  className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-foreground font-medium">{account.accountName}</p>
                      {account.isPrimary && <Badge variant="success">Primary</Badge>}
                      <Badge variant={account.status === "ACTIVE" ? "success" : "neutral"}>
                        {trustAccountStatusLabel(account.status)}
                      </Badge>
                    </div>
                    <p className="text-muted-foreground text-sm">
                      {account.bankName} &middot; {account.branchCode} &middot;{" "}
                      {account.accountNumber}
                    </p>
                  </div>
                  {/* ADR-379: the section in the Act's words, and an investment account's basis */}
                  <div className="text-right" data-testid="trust-account-section">
                    <p className="text-muted-foreground text-sm">
                      {TRUST_ACCOUNT_SECTION_LABELS[account.section]}
                    </p>
                    {account.investmentBasis && (
                      <p
                        className="text-muted-foreground text-xs"
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
      </SettingsSection>

      {/* Approval Settings Section */}
      <SettingsSection
        data-testid="approval-settings-section"
        title={
          <span className="inline-flex items-center gap-3">
            <Shield className="text-muted-foreground size-5" />
            Approval Settings
          </span>
        }
        description="Configure transaction approval requirements"
      >
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
                  className="border-border flex items-center justify-between rounded-lg border p-4"
                >
                  <div>
                    <p className="text-foreground font-medium">{account.accountName}</p>
                    <p className="text-muted-foreground text-sm">
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
      </SettingsSection>

      {/* Reminder Settings Section */}
      <SettingsSection
        data-testid="reminder-settings-section"
        title={
          <span className="inline-flex items-center gap-3">
            <Bell className="text-muted-foreground size-5" />
            Reminder Settings
          </span>
        }
        description="Configure trust-related notification reminders"
      >
        <div className="space-y-3 text-sm">
          <div className="border-border flex items-center justify-between rounded-lg border p-4">
            <div>
              <p className="text-foreground font-medium">Low Balance Alert</p>
              <p className="text-muted-foreground">
                Notify when a client&apos;s trust balance drops below a threshold
              </p>
            </div>
            <Badge variant="neutral">Configured per account</Badge>
          </div>
          <div className="border-border flex items-center justify-between rounded-lg border p-4">
            <div>
              <p className="text-foreground font-medium">Reconciliation Due</p>
              <p className="text-muted-foreground">
                Remind to complete monthly bank reconciliation
              </p>
            </div>
            <Badge variant="neutral">Monthly</Badge>
          </div>
          <div className="border-border flex items-center justify-between rounded-lg border p-4">
            <div>
              <p className="text-foreground font-medium">Investment Maturity</p>
              <p className="text-muted-foreground">Alert before fixed deposits mature</p>
            </div>
            <Badge variant="neutral">7 days before</Badge>
          </div>
        </div>
      </SettingsSection>
    </div>
  );
}
