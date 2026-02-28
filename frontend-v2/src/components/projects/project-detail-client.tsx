"use client";

import type {
  Project,
  Task,
  Customer,
  Document,
  ProjectMember,
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
  BudgetStatusResponse,
} from "@/lib/types";
import { DetailPage } from "@/components/layout/detail-page";
import { OverviewTab } from "@/components/projects/overview-tab";
import { TaskList } from "@/components/tasks/task-list";
import { CreateTaskDialog } from "@/components/tasks/create-task-dialog";
import { TimeSummaryTab } from "@/components/projects/time-summary-tab";
import { BudgetTab } from "@/components/projects/budget-tab";
import { TeamTab } from "@/components/projects/team-tab";
import { DocumentsTab } from "@/components/projects/documents-tab";
import { ActivityTab } from "@/components/projects/activity-tab";

interface ProjectDetailClientProps {
  slug: string;
  projectId: string;
  project: Project;
  tasks: Task[];
  members: ProjectMember[];
  customers: Customer[];
  documents: Document[];
  timeSummary: ProjectTimeSummary;
  timeSummaryByTask: TaskTimeSummary[];
  timeSummaryByMember: MemberTimeSummary[] | null;
  budgetStatus: BudgetStatusResponse | null;
  canEdit: boolean;
  isAdmin: boolean;
  onCreateTask: (
    slug: string,
    projectId: string,
    formData: FormData,
    assigneeId?: string | null
  ) => Promise<{ success: boolean; error?: string }>;
  onCreateTimeEntry: (
    slug: string,
    projectId: string,
    taskId: string,
    formData: FormData
  ) => Promise<{ success: boolean; error?: string }>;
  onUpdateProject: (
    slug: string,
    id: string,
    formData: FormData
  ) => Promise<{ success: boolean; error?: string }>;
}

export function ProjectDetailClient({
  slug,
  projectId,
  project,
  tasks,
  members,
  customers,
  documents,
  timeSummary,
  timeSummaryByTask,
  timeSummaryByMember,
  budgetStatus,
  canEdit,
  isAdmin,
  onCreateTask,
  onCreateTimeEntry,
  onUpdateProject,
}: ProjectDetailClientProps) {
  const doneTasks = tasks.filter((t) => t.status === "DONE").length;

  const tabs = [
    {
      id: "overview",
      label: "Overview",
      content: (
        <OverviewTab
          slug={slug}
          projectId={projectId}
          tasks={tasks}
          members={members}
          customers={customers}
          timeSummary={timeSummary}
          budgetStatus={budgetStatus}
        />
      ),
    },
    {
      id: "tasks",
      label: "Tasks",
      count: tasks.length,
      content: (
        <div className="space-y-4">
          {canEdit && (
            <div className="flex justify-end">
              <CreateTaskDialog
                slug={slug}
                projectId={projectId}
                members={members.map((m) => ({
                  id: m.memberId,
                  name: m.name,
                }))}
                onCreateTask={onCreateTask}
              />
            </div>
          )}
          <TaskList tasks={tasks} />
        </div>
      ),
    },
    {
      id: "time",
      label: "Time",
      content: (
        <TimeSummaryTab
          projectId={projectId}
          slug={slug}
          tasks={tasks}
          timeSummary={timeSummary}
          timeSummaryByTask={timeSummaryByTask}
          timeSummaryByMember={timeSummaryByMember}
          onCreateTimeEntry={onCreateTimeEntry}
        />
      ),
    },
    {
      id: "budget",
      label: "Budget",
      content: <BudgetTab budgetStatus={budgetStatus} />,
    },
    {
      id: "documents",
      label: "Docs",
      count: documents.length,
      content: <DocumentsTab documents={documents} />,
    },
    {
      id: "team",
      label: "Team",
      count: members.length,
      content: <TeamTab members={members} />,
    },
    {
      id: "activity",
      label: "Activity",
      content: <ActivityTab projectId={projectId} />,
    },
  ];

  return <DetailPage header={null} tabs={tabs} defaultTab="overview" />;
}
