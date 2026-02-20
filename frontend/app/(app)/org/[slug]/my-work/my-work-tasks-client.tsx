"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import type {
  MyWorkTaskItem,
  SavedViewResponse,
  CreateSavedViewRequest,
  TagResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

interface MyWorkTasksClientProps {
  assigned: MyWorkTaskItem[];
  unassigned: MyWorkTaskItem[];
  slug: string;
  orgRole: string;
  canManage: boolean;
  currentMemberId: string;
  members: { id: string; name: string; email: string }[];
  allTags: TagResponse[];
  fieldDefinitions: FieldDefinitionResponse[];
  fieldGroups: FieldGroupResponse[];
  groupMembers: Record<string, FieldGroupMemberResponse[]>;
  savedViews: SavedViewResponse[];
  onSave: (
    req: CreateSavedViewRequest,
  ) => Promise<{ success: boolean; error?: string }>;
}

export function MyWorkTasksClient({
  assigned,
  unassigned,
  slug,
  orgRole,
  canManage,
  currentMemberId,
  members,
  allTags,
  fieldDefinitions,
  fieldGroups,
  groupMembers,
  savedViews,
  onSave,
}: MyWorkTasksClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedTaskId = searchParams.get("taskId");

  function openTask(taskId: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("taskId", taskId);
    router.push(`?${params.toString()}`, { scroll: false });
  }

  function closeTask() {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("taskId");
    router.push(`?${params.toString()}`, { scroll: false });
  }

  const canCreateShared = orgRole === "org:admin" || orgRole === "org:owner";

  return (
    <div className="space-y-8">
      {/* View Selector */}
      <Suspense fallback={null}>
        <ViewSelectorClient
          entityType="TASK"
          views={savedViews}
          canCreate={canManage}
          canCreateShared={canCreateShared}
          slug={slug}
          allTags={allTags}
          fieldDefinitions={fieldDefinitions}
          onSave={onSave}
        />
      </Suspense>

      {/* Task Lists */}
      <UrgencyTaskList
        tasks={assigned}
        slug={slug}
        onTaskClick={openTask}
      />
      <AvailableTaskList
        tasks={unassigned}
        slug={slug}
        onTaskClick={openTask}
      />

      {/* Task Detail Sheet — cross-project (projectId=null skips guard) */}
      <TaskDetailSheet
        taskId={selectedTaskId}
        onClose={closeTask}
        projectId={null}
        slug={slug}
        canManage={canManage}
        currentMemberId={currentMemberId}
        orgRole={orgRole}
        members={members}
        allTags={allTags}
        fieldDefinitions={fieldDefinitions}
        fieldGroups={fieldGroups}
        groupMembers={groupMembers}
      />
    </div>
  );
}
