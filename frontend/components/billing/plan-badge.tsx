import { hasPlan } from "@/lib/auth";
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
  const isPro = await hasPlan("pro");

  return <PlanBadgeDisplay isPro={isPro} />;
}
