import { Info, Lock } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { HelpTip } from "@/components/help-tip";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { MethodBadge } from "@/components/billing/method-badge";
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
      return "Trial";
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
      {/* Page header */}
      <div className="flex items-center gap-3">
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Billing
          <HelpTip code="billing.overview" />
        </h1>
        <Badge variant={statusBadgeVariant(billing.status)}>{statusLabel(billing.status)}</Badge>
        <MethodBadge method={billing.billingMethod} />
      </div>

      {/* Managed account info card */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Info className="size-5 text-teal-600" />
            Managed Account
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Your account is managed by your administrator.
            {billing.status === "GRACE_PERIOD" && " Contact your administrator to restore access."}
          </p>
        </CardContent>
      </Card>

      {/* LOCKED override */}
      {billing.status === "LOCKED" && (
        <div className="flex min-h-[50vh] items-center justify-center">
          <Card className="max-w-md text-center">
            <CardHeader>
              <CardTitle className="flex items-center justify-center gap-2">
                <Lock className="size-5 text-red-500" />
                Account Locked
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Contact your administrator to restore access.
              </p>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
