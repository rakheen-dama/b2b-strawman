import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { ExecutionLog } from "@/components/automations/execution-log";
import { ExecutionDetail } from "@/components/automations/execution-detail";
import { AutomationsWidget } from "@/components/automations/automations-widget";
import type { AutomationExecutionResponse, PaginatedResponse } from "@/lib/api/automations";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/org/acme/settings/automations/executions",
}));

const mockFetchExecutions = vi.fn().mockResolvedValue({
  content: [],
  page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
});

vi.mock("@/app/(app)/org/[slug]/settings/automations/actions", () => ({
  fetchExecutionsAction: (...args: unknown[]) => mockFetchExecutions(...args),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

const sampleExecution: AutomationExecutionResponse = {
  id: "exec-1",
  ruleId: "rule-1",
  ruleName: "Auto-assign tasks",
  triggerEventType: "TASK_STATUS_CHANGED",
  triggerEventData: { taskId: "task-123", oldStatus: "TODO", newStatus: "IN_PROGRESS" },
  conditionsMet: true,
  status: "ACTIONS_COMPLETED",
  startedAt: "2026-03-07T10:00:00Z",
  completedAt: "2026-03-07T10:00:01.500Z",
  errorMessage: null,
  createdAt: "2026-03-07T10:00:00Z",
  actionExecutions: [
    {
      id: "action-exec-1",
      actionId: "action-1",
      actionType: "ASSIGN_MEMBER",
      status: "COMPLETED",
      scheduledFor: null,
      executedAt: "2026-03-07T10:00:01Z",
      resultData: { memberId: "member-1" },
      errorMessage: null,
      createdAt: "2026-03-07T10:00:00Z",
    },
  ],
};

const failedExecution: AutomationExecutionResponse = {
  id: "exec-2",
  ruleId: "rule-2",
  ruleName: "Send invoice email",
  triggerEventType: "INVOICE_STATUS_CHANGED",
  triggerEventData: { invoiceId: "inv-1" },
  conditionsMet: true,
  status: "ACTIONS_FAILED",
  startedAt: "2026-03-07T11:00:00Z",
  completedAt: "2026-03-07T11:00:02Z",
  errorMessage: "Email delivery failed",
  createdAt: "2026-03-07T11:00:00Z",
  actionExecutions: [
    {
      id: "action-exec-2",
      actionId: "action-2",
      actionType: "SEND_EMAIL",
      status: "FAILED",
      scheduledFor: null,
      executedAt: "2026-03-07T11:00:02Z",
      resultData: null,
      errorMessage: "SMTP connection timeout",
      createdAt: "2026-03-07T11:00:00Z",
    },
  ],
};

const samplePaginatedResponse: PaginatedResponse<AutomationExecutionResponse> = {
  content: [sampleExecution, failedExecution],
  page: { totalElements: 2, totalPages: 1, size: 20, number: 0 },
};

describe("ExecutionLog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders execution rows", () => {
    render(<ExecutionLog initialExecutions={samplePaginatedResponse} />);
    expect(screen.getByText("Auto-assign tasks")).toBeInTheDocument();
    expect(screen.getByText("Send invoice email")).toBeInTheDocument();
  });

  it("shows correct status badges", () => {
    render(<ExecutionLog initialExecutions={samplePaginatedResponse} />);
    expect(screen.getByText("Completed")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });

  it("opens detail sheet on row click", () => {
    render(<ExecutionLog initialExecutions={samplePaginatedResponse} />);
    fireEvent.click(screen.getByText("Auto-assign tasks"));
    expect(screen.getByText("Execution Detail")).toBeInTheDocument();
    expect(screen.getByText('Execution of "Auto-assign tasks"')).toBeInTheDocument();
  });

  it("shows empty state when no executions", () => {
    const empty: PaginatedResponse<AutomationExecutionResponse> = {
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
    };
    render(<ExecutionLog initialExecutions={empty} />);
    expect(screen.getByText("No executions found.")).toBeInTheDocument();
  });
});

describe("ExecutionDetail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows trigger event data", () => {
    render(<ExecutionDetail execution={sampleExecution} open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByText("taskId:")).toBeInTheDocument();
    expect(screen.getByText("task-123")).toBeInTheDocument();
  });

  it("shows per-action results", () => {
    render(<ExecutionDetail execution={sampleExecution} open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByText("Assign Member")).toBeInTheDocument();
    expect(screen.getByText("Actions (1)")).toBeInTheDocument();
  });
});

describe("AutomationsWidget", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders counts", () => {
    render(
      <AutomationsWidget
        data={{
          activeRulesCount: 5,
          todayTotal: 12,
          todaySucceeded: 10,
          todayFailed: 2,
        }}
        orgSlug="acme"
      />
    );
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("10 succeeded")).toBeInTheDocument();
  });

  it("shows failure badge when failures present", () => {
    render(
      <AutomationsWidget
        data={{
          activeRulesCount: 3,
          todayTotal: 8,
          todaySucceeded: 5,
          todayFailed: 3,
        }}
        orgSlug="acme"
      />
    );
    expect(screen.getByText("3 failed")).toBeInTheDocument();
  });
});
