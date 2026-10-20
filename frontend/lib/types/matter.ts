// ---- Projects (from ProjectController.java) ----

import type { TagResponse } from "./common";
import type { MatterRole } from "./member";

export type MatterStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED" | "CLOSED";

export type MatterPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Matter {
  id: string;
  name: string;
  description: string | null;
  status: MatterStatus;
  clientId: string | null;
  dueDate: string | null;
  referenceNumber: string | null;
  priority: MatterPriority | null;
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
  matterRole: MatterRole | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
}

export interface CreateMatterRequest {
  name: string;
  description?: string;
  clientId?: string;
  dueDate?: string;
  referenceNumber?: string;
  priority?: MatterPriority;
  workType?: string;
}

export interface UpdateMatterRequest {
  name: string;
  description?: string;
  clientId?: string | null;
  dueDate?: string | null;
  referenceNumber?: string | null;
  priority?: MatterPriority | null;
  workType?: string | null;
}

// ---- Time Summaries (from ProjectTimeSummaryController.java) ----

export interface MatterTimeSummary {
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

export interface MyWorkMatterSummary {
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
  byMatter: MyWorkMatterSummary[];
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

export interface MatterSetupStatus {
  matterId: string;
  clientAssigned: boolean;
  rateCardConfigured: boolean;
  budgetConfigured: boolean;
  teamAssigned: boolean;
  requiredFields: RequiredFieldStatus;
  completionPercentage: number;
  overallComplete: boolean;
}

export interface ClientReadiness {
  clientId: string;
  lifecycleStatus: string;
  checklistProgress: ChecklistProgress | null;
  requiredFields: RequiredFieldStatus;
  hasLinkedMatters: boolean;
  overallReadiness: string;
}

export interface MatterUnbilledBreakdown {
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
  byMatter: MatterUnbilledBreakdown[] | null;
}

export interface TemplateReadiness {
  templateId: string;
  templateName: string;
  templateSlug: string;
  ready: boolean;
  missingFields: string[];
}

/**
 * GAP-L-58 / E9.3 — fetches the upcoming court dates for a matter. Returns rows already filtered
 * to future-or-today and sorted by date ASC by the backend. The regulatory deadlines source was
 * removed in Phase 86 (Epic 604A), so the backend only emits COURT rows.
 */
export interface MatterUpcomingDeadline {
  type: "COURT";
  date: string;
  description: string;
  status: string | null;
}
