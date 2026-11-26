import { Info, Lock } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { PageHeader } from "@b2mash/ui/page-header";
import { HelpTip } from "@/components/help-tip";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { MethodBadge } from "@/components/billing/method-badge";
import { SettingsSection } from "@/components/settings/settings-section";
import { getSubscription } from "@/lib/actions/subscription";

type BadgeVariant = "neutral" | "success" | "warning" | "destructive";

function statusBadgeVariant(status: string): BadgeVariant {
  switch (status) {
    case "TRIALING":
      return "neutral";
    case "ACTIVE":
      return "success";
    case "PENDING_CANCELLATION":
    case "PAST_DUE":
      return "warning";
    case "GRACE_PERIOD":
    case "EXPIRED":
    case "SUSPENDED":
    case "LOCKED":
      return "destructive";
    default:
      return "neutral";
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "TRIALING":
      return "Trial period";
    case "ACTIVE":
      return "Active";
    case "PENDING_CANCELLATION":
      return "Cancelling";
    case "PAST_DUE":
      return "Past Due";
    case "GRACE_PERIOD":
      return "Grace Period";
    case "EXPIRED":
      return "Expired";
    case "SUSPENDED":
      return "Suspended";
    case "LOCKED":
      return "Locked";
    default:
      return status;
  }
}

/**
 * Firm › Subscription tab (Phase 87 S9, ADR-341) — the former settings/billing page's loader.
 * Manual firm-level billing is the only model (ADR-335): every tenant is admin-managed, so the tab
 * shows the billing method and subscription status only. Needs no slug.
 */
export async function renderSubscriptionTab() {
  const billing = await getSubscription();

  return (
    <div className="space-y-8">
      {/* K-106 (founder-approved copy): the h1 matches the tab label "Subscription". */}
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Subscription
            <HelpTip code="billing.overview" />
          </span>
        }
      >
        <Badge variant={statusBadgeVariant(billing.status)}>{statusLabel(billing.status)}</Badge>
        {/* K-106: "Invoiced manually" on this tab only — MethodBadge is shared with platform admin. */}
        {billing.billingMethod === "MANUAL" ? (
          <Badge variant="neutral">Invoiced manually</Badge>
        ) : (
          <MethodBadge method={billing.billingMethod} />
        )}
      </PageHeader>

      {/* Managed account info (read-only) */}
      <SettingsSection
        title={
          <span className="inline-flex items-center gap-2">
            <Info className="size-5 text-teal-600" />
            Managed Account
          </span>
        }
      >
        <p className="text-muted-foreground text-sm">
          Your Kazi subscription is managed by b2mash. Contact support to change your plan or
          billing details.
          {billing.status === "GRACE_PERIOD" && " Contact your administrator to restore access."}
        </p>
      </SettingsSection>

      {/* LOCKED override */}
      {billing.status === "LOCKED" && (
        <div className="flex min-h-[50vh] items-center justify-center">
          <Card className="max-w-md text-center">
            <CardHeader>
              <CardTitle className="flex items-center justify-center gap-2">
                <Lock className="text-destructive size-5" />
                Account Locked
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-sm">
                Contact your administrator to restore access.
              </p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
