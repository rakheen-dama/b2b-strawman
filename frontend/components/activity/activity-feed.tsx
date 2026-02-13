import { Activity } from "lucide-react";
import { fetchProjectActivity } from "@/lib/actions/activity";
import { EmptyState } from "@/components/empty-state";
import { ActivityFeedClient } from "@/components/activity/activity-feed-client";

interface ActivityFeedProps {
  projectId: string;
  orgSlug: string;
}

export async function ActivityFeed({ projectId }: ActivityFeedProps) {
  let initialItems: Awaited<ReturnType<typeof fetchProjectActivity>> | null =
    null;

  try {
    initialItems = await fetchProjectActivity(projectId, undefined, 0, 20);
  } catch {
    // Non-fatal: show empty state if initial fetch fails
  }

  if (!initialItems || initialItems.content.length === 0) {
    return (
      <EmptyState
        icon={Activity}
        title="No activity yet"
        description="Activity will appear here as team members work on this project."
      />
    );
  }

  return (
    <ActivityFeedClient
      projectId={projectId}
      initialItems={initialItems.content}
      initialTotalPages={initialItems.totalPages}
    />
  );
}
