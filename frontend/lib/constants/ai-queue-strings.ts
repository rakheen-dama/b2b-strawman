/**
 * i18n string constants for the AI Review Queue UI.
 * Centralized here for future extraction to next-intl or similar.
 */
export const AI_QUEUE_STRINGS = {
  title: "AI Review Queue",
  empty:
    "No pending AI suggestions. Suggestions appear here when automation rules invoke AI specialists.",
  status: {
    RUNNING: "Running",
    PENDING_APPROVAL: "Pending",
    APPROVED: "Approved",
    REJECTED: "Rejected",
    AUTO_APPLIED: "Auto-applied",
    FAILED: "Failed",
    EXPIRED: "Expired",
  } as Record<string, string>,
  filter: {
    status: "Status",
    specialist: "Specialist",
    dateRange: "Date range",
    context: "Context",
    actor: "Actor",
  },
  bulkApprove: {
    cta: (count: number) => `Approve ${count}`,
    approveAllRemaining: "Approve All Remaining",
    sameSpecialistError: "All selected invocations must belong to the same specialist.",
    capExceeded: "Maximum 25 invocations can be bulk-approved at once.",
  },
  drawer: {
    proposedOutput: "Proposed Changes",
    llmCalls: "AI Usage",
    approve: "Approve",
    reject: "Reject",
    retry: "Retry",
  },
  pendingWidget: {
    title: "Pending AI Suggestions",
    approve: "Approve",
    reject: "Reject",
  },
  specialist: {
    BILLING: "Billing",
    INTAKE: "Intake",
    INBOX: "Inbox",
  } as Record<string, string>,
} as const;
