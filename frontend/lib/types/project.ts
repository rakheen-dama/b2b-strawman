// ---- Projects (from ProjectController.java) ----

import type { TagResponse } from "./common";
import type { ProjectRole } from "./member";

export type ProjectStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED" | "CLOSED";

export type ProjectPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Project {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  clientId: string | null;
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
  closedAt: string | null;
  /**
   * Stamped on first ACTIVE→COMPLETED or ACTIVE→CLOSED transition (ADR-249) and preserved across
   * reopens. Anchors the per-matter retention clock for legal-vertical CLOSED matters.
   */
  retentionClockStartedAt: string | null;
  /**
   * GAP-OBS-Day60-RetentionShape — server-computed retention end date for CLOSED matters,
   * derived from `retentionClockStartedAt + orgSettings.legalMatterRetentionYears`. Null when
   * the matter isn't closed, the clock isn't stamped, or the org's retention years aren't
   * configured.
   */
  retentionEndsOn: string | null;
  matterRole: ProjectRole | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  clientId?: string;
  dueDate?: string;
  referenceNumber?: string;
  priority?: ProjectPriority;
  workType?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
  clientId?: string | null;
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
  matterId: string;
  matterName: string;
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
  matterId: string;
  matterName: string;
  date: string;
  durationMinutes: number;
  billable: boolean;
  description: string | null;
}

export interface MyWorkProjectSummary {
  matterId: string;
  matterName: string;
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
  byMatter: MyWorkProjectSummary[];
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
  matterId: string;
  clientAssigned: boolean;
  rateCardConfigured: boolean;
  budgetConfigured: boolean;
  teamAssigned: boolean;
  requiredFields: RequiredFieldStatus;
  completionPercentage: number;
  overallComplete: boolean;
}

export interface CustomerReadiness {
  clientId: string;
  lifecycleStatus: string;
  checklistProgress: ChecklistProgress | null;
  requiredFields: RequiredFieldStatus;
  hasLinkedMatters: boolean;
  overallReadiness: string;
}

export interface ProjectUnbilledBreakdown {
  matterId: string;
  matterName: string;
  hours: number;
  amount: number;
  entryCount: number;
}

export interface UnbilledTimeSummary {
  totalHours: number;
  totalAmount: number;
  currency: string;
  entryCount: number;
  byMatter: ProjectUnbilledBreakdown[] | null;
}

export interface TemplateReadiness {
  templateId: string;
  templateName: string;
  templateSlug: string;
  ready: boolean;
  missingFields: string[];
}
