import type { LucideIcon } from "lucide-react";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: React.ReactNode;
}

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <Icon className="size-16 text-slate-300 dark:text-slate-700" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">{title}</h2>
      <p className="text-sm text-slate-600 dark:text-slate-400">{description}</p>
      {action}
    </div>
  );
}
