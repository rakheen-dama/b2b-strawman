// ---- Tasks (from TaskController.java) ----

import type { TagResponse } from "./common";

export type TaskStatus = "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  type: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  createdBy: string;
  createdByName: string | null;
  dueDate: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  completedBy: string | null;
  completedByName: string | null;
  cancelledAt: string | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  tags?: TagResponse[];
  recurrenceRule: string | null;
  recurrenceEndDate: string | null;
  parentTaskId: string | null;
  isRecurring: boolean;
}

export interface CompleteTaskResponse extends Task {
  nextInstance: Task | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority?: TaskPriority;
  type?: string;
  dueDate?: string;
  assigneeId?: string;
  recurrenceRule?: string;
  recurrenceEndDate?: string;
}

export interface UpdateTaskRequest {
  title: string;
  description?: string;
  priority: TaskPriority;
  status: TaskStatus;
  type?: string;
  dueDate?: string;
  assigneeId?: string;
  recurrenceRule?: string;
  recurrenceEndDate?: string;
}

// ---- Task Items (from TaskItemController.java) ----

export interface TaskItem {
  id: string;
  taskId: string;
  title: string;
  completed: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

// ---- Time Entries (from TimeEntryController.java) ----

export interface TimeEntry {
  id: string;
  taskId: string;
  memberId: string;
  memberName: string;
  date: string;
  durationMinutes: number;
  billable: boolean;
  rateCents: number | null;
  billingRateSnapshot: number | null;
  billingRateCurrency: string | null;
  costRateSnapshot: number | null;
  costRateCurrency: string | null;
  billableValue: number | null;
  costValue: number | null;
  description: string | null;
  invoiceId: string | null;
  invoiceNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTimeEntryRequest {
  date: string;
  durationMinutes: number;
  billable?: boolean;
  rateCents?: number;
  description?: string;
}

export interface UpdateTimeEntryRequest {
  date?: string;
  durationMinutes?: number;
  billable?: boolean;
  rateCents?: number;
  description?: string;
}
