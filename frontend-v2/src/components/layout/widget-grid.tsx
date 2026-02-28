import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

import {
  Card,
  CardHeader,
  CardTitle,
  CardAction,
  CardContent,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface WidgetGridProps {
  children: ReactNode;
  className?: string;
}

export function WidgetGrid({ children, className }: WidgetGridProps) {
  return (
    <div
      className={cn("grid grid-cols-1 gap-6 lg:grid-cols-2", className)}
    >
      {children}
    </div>
  );
}

interface WidgetCardProps {
  title: string;
  viewAllHref?: string;
  viewAllLabel?: string;
  children: ReactNode;
  className?: string;
}

export function WidgetCard({
  title,
  viewAllHref,
  viewAllLabel = "View all",
  children,
  className,
}: WidgetCardProps) {
  return (
    <Card className={cn("gap-4", className)}>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-slate-700">
          {title}
        </CardTitle>
        {viewAllHref && (
          <CardAction>
            <Link
              href={viewAllHref}
              className="inline-flex items-center gap-1 text-xs font-medium text-teal-600 transition-colors hover:text-teal-700"
            >
              {viewAllLabel}
              <ArrowRight className="h-3 w-3" />
            </Link>
          </CardAction>
        )}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
