import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Users, UserPlus, UserCheck, Moon, UserX } from "lucide-react";
import { cn } from "@/lib/utils";

interface LifecycleDistributionSectionProps {
  counts: Record<string, number>;
  orgSlug: string;
}

const STATUSES = [
  { key: "PROSPECT", label: "Prospect", icon: UserPlus, color: "text-slate-500" },
  { key: "ONBOARDING", label: "Onboarding", icon: Users, color: "text-blue-500" },
  { key: "ACTIVE", label: "Active", icon: UserCheck, color: "text-emerald-500" },
  { key: "DORMANT", label: "Dormant", icon: Moon, color: "text-amber-500" },
  { key: "OFFBOARDED", label: "Offboarded", icon: UserX, color: "text-red-500" },
] as const;

export function LifecycleDistributionSection({
  counts,
  orgSlug,
}: LifecycleDistributionSectionProps) {
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Lifecycle Distribution
      </h2>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {STATUSES.map(({ key, label, icon: Icon, color }) => (
          <Link
            key={key}
            href={`/org/${orgSlug}/customers?lifecycleStatus=${key}`}
            className="block h-full"
          >
            <Card className="h-full p-4 transition-shadow hover:shadow-md">
              <div className="flex items-center gap-3">
                <Icon className={cn("h-5 w-5", color)} />
                <div>
                  <p className="font-mono text-2xl font-semibold tabular-nums text-slate-950 dark:text-slate-50">
                    {counts[key] ?? 0}
                  </p>
                  <p className="text-sm text-slate-500 dark:text-slate-400">{label}</p>
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
