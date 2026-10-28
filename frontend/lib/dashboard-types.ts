// Dashboard API response types

export interface TrendPoint {
  period: string;
  value: number;
}

export interface MatterHealth {
  matterId: string;
  matterName: string;
  clientName: string | null;
  healthStatus: "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN";
  healthReasons: string[];
  tasksDone: number;
  tasksTotal: number;
  completionPercent: number;
  budgetConsumedPercent: number | null;
  hoursLogged: number;
}

export interface CrossMatterActivityItem {
  eventId: string;
  eventType: string;
  description: string;
  actorName: string;
  matterId: string;
  matterName: string;
  occurredAt: string;
}

export interface PersonalUtilization {
  totalHours: number;
  billableHours: number;
  billablePercent: number;
}

export interface PersonalMatterBreakdown {
  matterId: string;
  matterName: string;
  hours: number;
  percent: number;
}

export interface PersonalDeadline {
  taskId: string;
  taskName: string;
  matterName: string;
  dueDate: string;
}

export interface PersonalDashboardResponse {
  utilization: PersonalUtilization;
  matterBreakdown: PersonalMatterBreakdown[];
  overdueTaskCount: number;
  upcomingDeadlines: PersonalDeadline[];
  trend: TrendPoint[];
}

// Matter health detail (from GET /api/matters/{id}/health)
export interface MatterHealthDetail {
  healthStatus: "HEALTHY" | "AT_RISK" | "CRITICAL" | "UNKNOWN";
  healthReasons: string[];
  metrics: MatterHealthMetrics;
}

export interface MatterHealthMetrics {
  tasksDone: number;
  tasksInProgress: number;
  tasksTodo: number;
  tasksOverdue: number;
  totalTasks: number;
  completionPercent: number;
  budgetConsumedPercent: number | null;
  hoursThisPeriod: number;
  daysSinceLastActivity: number;
}

// Task summary (from GET /api/matters/{id}/task-summary)
export interface TaskSummaryResponse {
  todo: number;
  inProgress: number;
  done: number;
  cancelled: number;
  total: number;
  overdueCount: number;
}

// Member hours (from GET /api/matters/{id}/member-hours)
export interface MemberHoursEntry {
  memberId: string;
  memberName: string;
  totalHours: number;
  billableHours: number;
}

// Legal-native home reads (Phase 87 614A/614B, arch §12.2.6). Nulls are sent as JSON null.

/** GET /api/dashboard/unbilled-wip — one fee earner's unbilled (billable, not invoiced) time. */
export interface FeeEarnerWip {
  memberId: string;
  memberName: string;
  minutes: number;
  /** null when the response is redacted. */
  value: number | null;
  /** yyyy-MM-dd — oldest unbilled entry. */
  oldestEntryDate: string;
}

export interface UnbilledWipResponse {
  /** yyyy-MM-dd (server date). */
  asOf: string;
  /** Org default currency. */
  currency: string;
  /** Redacted: the caller's own minutes, never the firm total. */
  totalMinutes: number;
  totalValue: number | null;
  /** null when redacted OR module `disbursements` off — hide the line. */
  unbilledDisbursementTotal: number | null;
  /** Value desc, minutes desc, name. Redacted: caller's own row only (may be empty). */
  byFeeEarner: FeeEarnerWip[];
  redacted: boolean;
}

export type FicaOutstandingStatus = "NOT_STARTED" | "IN_PROGRESS";

export interface FicaOutstandingClient {
  clientId: string;
  clientName: string;
  lifecycleStatus: "ONBOARDING" | "ACTIVE";
  ficaStatus: FicaOutstandingStatus;
  /** null when FICA has not been started. */
  requestId: string | null;
  /** ISO instant — lifecycleStatusChangedAt ?? createdAt. */
  openedAt: string;
  daysOutstanding: number;
}

/** GET /api/dashboard/fica-outstanding?limit= (CUSTOMER_MANAGEMENT). */
export interface FicaOutstandingResponse {
  outstandingCount: number;
  totalActiveClients: number;
  /** Top `limit` by daysOutstanding desc, then name. */
  clients: FicaOutstandingClient[];
  limit: number;
}

export interface TrustAccountRef {
  id: string;
  accountName: string;
  isPrimary: boolean;
}

/** GET /api/dashboard/trust-position (VIEW_TRUST + module trust_accounting; 404 = no account). */
export interface TrustPositionResponse {
  trustAccountId: string;
  accountName: string;
  bankName: string;
  cashbookBalance: number;
  clientLedgerTotal: number;
  /** cashbookBalance − clientLedgerTotal; 0 = balanced. */
  difference: number;
  lastReconciliationPeriodEnd: string | null;
  lastReconciliationStatus: "DRAFT" | "COMPLETED" | null;
  lastReconciliationBalanced: boolean | null;
  daysSinceReconciliation: number | null;
  pendingApprovalCount: number;
  accounts: TrustAccountRef[];
}
