import Link from "next/link";
import { cn } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { ActionCardProps } from "./types";

export function ActionCard({
  icon: Icon,
  title,
  description,
  primaryAction,
  secondaryAction,
  variant = "default",
}: ActionCardProps) {
  return (
    <Card
      className={cn(
        variant === "accent" && "bg-teal-50/50 dark:bg-teal-950/20",
      )}
    >
      <CardContent className="flex items-start gap-4">
        <Icon className="mt-0.5 h-5 w-5 shrink-0 text-slate-500" />
        <div className="flex-1 space-y-2">
          <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {title}
          </h3>
          <p className="text-sm text-muted-foreground">{description}</p>
          {(primaryAction || secondaryAction) && (
            <div className="flex items-center gap-2 pt-1">
              {primaryAction && (
                <Button asChild size="sm">
                  <Link href={primaryAction.href}>{primaryAction.label}</Link>
                </Button>
              )}
              {secondaryAction && (
                <Button asChild size="sm" variant="outline">
                  <Link href={secondaryAction.href}>
                    {secondaryAction.label}
                  </Link>
                </Button>
              )}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
