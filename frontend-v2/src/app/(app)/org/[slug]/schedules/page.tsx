import { PageHeader } from "@/components/layout/page-header";
import { ScheduleList } from "@/components/schedules/schedule-list";

export default async function SchedulesPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Schedules"
        description="Manage recurring tasks and scheduled work"
      />
      <ScheduleList />
    </div>
  );
}
