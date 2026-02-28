import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface PageHeaderProps {
  title: string;
  description?: string;
  count?: number;
  backHref?: string;
  actions?: ReactNode;
  className?: string;
}

export function PageHeader({
  title,
  description,
  count,
  backHref,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {backHref && (
            <Link
              href={backHref}
              className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
              aria-label="Go back"
            >
              <ArrowLeft className="h-4 w-4" />
            </Link>
          )}
          <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
            {title}
          </h1>
          {count !== undefined && (
            <Badge variant="neutral" className="tabular-nums">
              {count}
            </Badge>
          )}
        </div>
        {actions && <div className="flex items-center gap-2">{actions}</div>}
      </div>
      {description && (
        <p className={cn("text-sm text-slate-500", backHref && "ml-11")}>
          {description}
        </p>
      )}
    </div>
  );
}
