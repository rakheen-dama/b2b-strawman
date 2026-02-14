// Dashboard API response types

export interface TrendPoint {
  period: string;
  value: number;
}

export interface KpiValues {
  activeProjectCount: number;
  totalHoursLogged: number;
  billablePercent: number | null;
  overdueTaskCount: number;
  averageMarginPercent: number | null;
}

export interface KpiResponse extends KpiValues {
  trend: TrendPoint[];
  previousPeriod: KpiValues;
}

export interface ProjectHealth {
  projectId: string;
  projectName: string;
  customerName: string | null;
  healthStatus: "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN";
  healthReasons: string[];
  tasksDone: number;
  tasksTotal: number;
  completionPercent: number;
  budgetConsumedPercent: number | null;
  hoursLogged: number;
}

export interface ProjectWorkloadEntry {
  projectId: string;
  projectName: string;
  hours: number;
}

export interface TeamWorkloadEntry {
  memberId: string;
  memberName: string;
  totalHours: number;
  billableHours: number;
  projects: ProjectWorkloadEntry[];
}

export interface CrossProjectActivityItem {
  eventId: string;
  eventType: string;
  description: string;
  actorName: string;
  projectId: string;
  projectName: string;
  occurredAt: string;
}

export interface PersonalUtilization {
  totalHours: number;
  billableHours: number;
  billablePercent: number;
}

export interface PersonalProjectBreakdown {
  projectId: string;
  projectName: string;
  hours: number;
  percent: number;
}

export interface PersonalDeadline {
  taskId: string;
  taskName: string;
  projectName: string;
  dueDate: string;
}

export interface PersonalDashboardResponse {
  utilization: PersonalUtilization;
  projectBreakdown: PersonalProjectBreakdown[];
  overdueTaskCount: number;
  upcomingDeadlines: PersonalDeadline[];
  trend: TrendPoint[];
}
