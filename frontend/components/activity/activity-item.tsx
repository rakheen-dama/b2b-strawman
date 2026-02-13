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
        <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-olive-200 text-xs font-medium text-olive-700 dark:bg-olive-700 dark:text-olive-200">
          {getInitials(item.actorName)}
        </span>
      )}

      {/* Content */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <Icon className="size-3.5 shrink-0 text-olive-400 dark:text-olive-500" />
          <p className="text-sm text-olive-700 dark:text-olive-300">
            {item.message}
          </p>
        </div>
        <p className="mt-0.5 text-xs text-olive-500 dark:text-olive-400">
          {formatRelativeDate(item.occurredAt)}
        </p>
      </div>
    </div>
  );
}
