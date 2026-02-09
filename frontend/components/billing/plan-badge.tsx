import { auth } from "@clerk/nextjs/server";
import { Badge } from "@/components/ui/badge";

interface PlanBadgeDisplayProps {
  isPro: boolean;
}

/** Presentational plan badge — accepts `isPro` directly. */
export function PlanBadgeDisplay({ isPro }: PlanBadgeDisplayProps) {
  return (
    <Badge variant={isPro ? "default" : "secondary"}>
      {isPro ? "Pro" : "Starter"}
    </Badge>
  );
}

/** Server component that auto-detects the org's plan from Clerk session. */
export async function PlanBadge() {
  const { has } = await auth();
  const isPro = has({ plan: "pro" }) ?? false;

  return <PlanBadgeDisplay isPro={isPro} />;
}
