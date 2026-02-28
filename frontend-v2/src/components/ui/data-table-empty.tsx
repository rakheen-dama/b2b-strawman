import * as React from "react";

import { cn } from "@/lib/utils";

interface DataTableEmptyProps {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

function DataTableEmpty({
  icon,
  title,
  description,
  action,
  className,
}: DataTableEmptyProps) {
  return (
    <div
      className={cn(
        "flex min-h-[300px] flex-col items-center justify-center rounded-lg bg-slate-50/50 px-6 py-12 text-center dark:bg-slate-900/50",
        className
      )}
    >
      {icon && (
        <div className="mb-4 text-slate-400 dark:text-slate-500 [&_svg]:size-10">
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-slate-700 dark:text-slate-200">
        {title}
      </h3>
      {description && (
        <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export { DataTableEmpty, type DataTableEmptyProps };
