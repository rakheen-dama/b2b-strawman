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
    <Card className="border-none bg-muted/30">
      <CardContent className="flex flex-col items-center py-8 text-center">
        <Icon className="mb-3 h-10 w-10 text-muted-foreground/60" />
        <h3 className="mb-1 text-sm font-medium">{title}</h3>
        <p className="mb-4 max-w-sm text-sm text-muted-foreground">
          {description}
        </p>
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
