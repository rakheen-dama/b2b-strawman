import { Check, Users } from "lucide-react";
import { api } from "@/lib/api";
import { PlanBadgeDisplay } from "@/components/billing/plan-badge";
import { UpgradeCard } from "@/components/billing/upgrade-card";
import { Progress } from "@/components/ui/progress";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { BillingResponse } from "@/lib/internal-api";

const PLAN_FEATURES = [
  { feature: "Team members", starter: "Up to 2", pro: "Up to 10" },
  { feature: "Infrastructure", starter: "Shared", pro: "Dedicated" },
  { feature: "Data isolation", starter: "Row-level", pro: "Schema-level" },
  { feature: "Support", starter: "Community", pro: "Priority" },
];

export default async function BillingPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const billing = await api.get<BillingResponse>("/api/billing/subscription");

  const isPro = billing.tier === "PRO";
  const { maxMembers, currentMembers } = billing.limits;
  const usagePercent = maxMembers > 0 ? Math.round((currentMembers / maxMembers) * 100) : 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Billing</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Manage your organization&apos;s subscription and billing.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Current Plan
            <PlanBadgeDisplay isPro={isPro} />
          </CardTitle>
          <CardDescription>
            {isPro
              ? "You're on the Pro plan with dedicated infrastructure and higher limits."
              : "You're on the Starter plan with shared infrastructure."}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-1.5">
                <Users className="h-4 w-4 text-muted-foreground" />
                Members
              </span>
              <span className="text-muted-foreground">
                {currentMembers} of {maxMembers}
              </span>
            </div>
            <Progress value={usagePercent} />
          </div>
        </CardContent>
      </Card>

      {!isPro && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Compare Plans</CardTitle>
              <CardDescription>
                See what you get with each plan.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-3 gap-4 text-sm">
                <div />
                <div className="font-medium">Starter</div>
                <div className="font-medium">Pro</div>
                {PLAN_FEATURES.map(({ feature, starter, pro }) => (
                  <div key={feature} className="contents">
                    <div className="text-muted-foreground">{feature}</div>
                    <div>{starter}</div>
                    <div className="flex items-center gap-1.5">
                      <Check className="h-3.5 w-3.5 text-green-600" />
                      {pro}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <UpgradeCard slug={slug} />
        </>
      )}
    </div>
  );
}
