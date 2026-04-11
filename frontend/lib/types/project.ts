// ---- Projects (from ProjectController.java) ----

import type { TagResponse } from "./common";
import type { ProjectRole } from "./member";

export type ProjectStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED";

export type ProjectPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Project {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  customerId: string | null;
  dueDate: string | null;
  referenceNumber: string | null;
  priority: ProjectPriority | null;
  workType: string | null;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  archivedAt: string | null;
  projectRole: ProjectRole | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  customerId?: string;
  dueDate?: string;
  referenceNumber?: string;
  priority?: ProjectPriority;
  workType?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
  customerId?: string | null;
  dueDate?: string | null;
  referenceNumber?: string | null;
  priority?: ProjectPriority | null;
  workType?: string | null;
}

// ---- Time Summaries (from ProjectTimeSummaryController.java) ----

export interface ProjectTimeSummary {
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
  contributorCount: number;
  entryCount: number;
}

export interface MemberTimeSummary {
  memberId: string;
  memberName: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
}

export interface TaskTimeSummary {
  taskId: string;
  taskTitle: string;
  billableMinutes: number;
  totalMinutes: number;
  entryCount: number;
}

// ---- My Work (from MyWorkController.java) ----

export interface MyWorkTaskItem {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  status: string;
  priority: string;
  dueDate: string | null;
  totalTimeMinutes: number;
}

export interface MyWorkTasksResponse {
  assigned: MyWorkTaskItem[];
  unassigned: MyWorkTaskItem[];
}

export interface MyWorkTimeEntryItem {
  id: string;
  taskId: string;
  taskTitle: string;
  projectId: string;
  projectName: string;
  date: string;
  durationMinutes: number;
  billable: boolean;
  description: string | null;
}

export interface MyWorkProjectSummary {
  projectId: string;
  projectName: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
}

export interface MyWorkTimeSummary {
  memberId: string;
  fromDate: string;
  toDate: string;
  billableMinutes: number;
  nonBillableMinutes: number;
  totalMinutes: number;
  byProject: MyWorkProjectSummary[];
}

// ---- Setup Status ----

export interface FieldStatus {
  name: string;
  slug: string;
  filled: boolean;
}

export interface RequiredFieldStatus {
  filled: number;
  total: number;
  fields: FieldStatus[];
}

export interface ChecklistProgress {
  checklistName: string;
  completed: number;
  total: number;
  percentComplete: number;
}

export interface ProjectSetupStatus {
  projectId: string;
  customerAssigned: boolean;
  rateCardConfigured: boolean;
  budgetConfigured: boolean;
  teamAssigned: boolean;
  requiredFields: RequiredFieldStatus;
  completionPercentage: number;
  overallComplete: boolean;
}

export interface CustomerReadiness {
  customerId: string;
  lifecycleStatus: string;
  checklistProgress: ChecklistProgress | null;
  requiredFields: RequiredFieldStatus;
  hasLinkedProjects: boolean;
  overallReadiness: string;
}

export interface ProjectUnbilledBreakdown {
  projectId: string;
  projectName: string;
  hours: number;
  amount: number;
  entryCount: number;
}

export interface UnbilledTimeSummary {
  totalHours: number;
  totalAmount: number;
  currency: string;
  entryCount: number;
  byProject: ProjectUnbilledBreakdown[] | null;
}

export interface TemplateReadiness {
  templateId: string;
  templateName: string;
  templateSlug: string;
  ready: boolean;
  missingFields: string[];
}
