import { Sparkles, Users } from "lucide-react";
import { api } from "@/lib/api";
import { PlanBadgeDisplay } from "@/components/billing/plan-badge";
import { Progress } from "@/components/ui/progress";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { BillingResponse } from "@/lib/internal-api";

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
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-5 w-5" />
              Upgrade to Pro
            </CardTitle>
            <CardDescription>
              Unlock dedicated infrastructure, higher member limits, and priority
              support for your team.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Contact us at{" "}
              <a
                href={`mailto:sales@docteams.com?subject=Upgrade ${slug} to Pro`}
                className="font-medium text-primary underline underline-offset-4 hover:text-primary/80"
              >
                sales@docteams.com
              </a>{" "}
              to upgrade your organization.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
