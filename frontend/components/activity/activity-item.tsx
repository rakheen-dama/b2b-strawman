import {
  CheckSquare,
  FileText,
  MessageSquare,
  Clock,
  Users,
  Activity,
  type LucideIcon,
} from "lucide-react";
import { formatRelativeDate } from "@/lib/format";
import type { ActivityItem as ActivityItemType } from "@/lib/actions/activity";

interface ActivityItemProps {
  item: ActivityItemType;
}

const ENTITY_ICON_MAP: Record<string, LucideIcon> = {
  task: CheckSquare,
  document: FileText,
  comment: MessageSquare,
  time_entry: Clock,
  project_member: Users,
};

function getInitials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

export function ActivityItem({ item }: ActivityItemProps) {
  const Icon = ENTITY_ICON_MAP[item.entityType] ?? Activity;

  return (
    <div className="flex items-start gap-3 px-3 py-2.5">
      {/* Avatar */}
      {item.actorAvatarUrl ? (
        <img
          src={item.actorAvatarUrl}
          alt={item.actorName}
          className="mt-0.5 size-7 shrink-0 rounded-full"
        />
      ) : (
        <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-200">
          {getInitials(item.actorName)}
        </span>
      )}

      {/* Content */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <Icon className="size-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
          <p className="text-sm text-slate-700 dark:text-slate-300">
            {item.message}
          </p>
        </div>
        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
          {formatRelativeDate(item.occurredAt)}
        </p>
      </div>
    </div>
  );
}
