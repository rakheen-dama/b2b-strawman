"use client";

import Link from "next/link";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { EmptyStateProps } from "./types";

export function EmptyState({
  icon: Icon,
  title,
  description,
  actionLabel,
  actionHref,
  onAction,
}: EmptyStateProps) {
  return (
    <Card className="bg-muted/30 border-none">
      <CardContent className="flex flex-col items-center py-8 text-center">
        <Icon className="text-muted-foreground/60 mb-3 h-10 w-10" />
        <h3 className="mb-1 text-sm font-medium">{title}</h3>
        <p className="text-muted-foreground mb-4 max-w-sm text-sm">{description}</p>
        {actionLabel && actionHref && (
          <Button asChild size="sm" variant="outline">
            <Link href={actionHref}>{actionLabel}</Link>
          </Button>
        )}
        {actionLabel && onAction && !actionHref && (
          <Button size="sm" variant="outline" onClick={onAction}>
            {actionLabel}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
