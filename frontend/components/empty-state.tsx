import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

interface EmptyStateProps {
  icon: LucideIcon;
  title: React.ReactNode;
  description: React.ReactNode;
  action?: React.ReactNode;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
  secondaryLink?: { label: string; href: string };
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  actionLabel,
  actionHref,
  onAction,
  secondaryLink,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <Icon className="size-16 text-slate-300 dark:text-slate-700" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
        {title}
      </h2>
      <p className="max-w-md text-sm text-slate-600 dark:text-slate-400">
        {description}
      </p>
      {actionLabel && onAction && (
        <Button size="sm" variant="outline" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
      {actionLabel && actionHref && !onAction && (
        <Button asChild size="sm" variant="outline">
          <Link href={actionHref}>{actionLabel}</Link>
        </Button>
      )}
      {action}
      {secondaryLink && (
        <Link
          href={secondaryLink.href}
          className="text-sm text-teal-600 hover:text-teal-700"
        >
          {secondaryLink.label}
        </Link>
      )}
    </div>
  );
}
