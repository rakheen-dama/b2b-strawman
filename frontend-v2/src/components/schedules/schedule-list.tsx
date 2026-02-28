import { CalendarClock } from "lucide-react";
import { EmptyState } from "@/components/empty-state";

export function ScheduleList() {
  return (
    <EmptyState
      icon={CalendarClock}
      title="Recurring Schedules"
      description="Recurring schedule management will be available in a future update."
    />
  );
}
