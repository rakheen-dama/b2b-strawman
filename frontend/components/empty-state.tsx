"use client";

import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: React.ReactNode;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  actionLabel,
  actionHref,
  onAction,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <Icon className="size-16 text-slate-300 dark:text-slate-700" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
        {title}
      </h2>
      <p className="text-sm text-slate-600 dark:text-slate-400">
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
      {action}
    </div>
  );
}
