import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { RuleList } from "@/components/automations/rule-list";
import type {
  AutomationRuleResponse,
  TemplateDefinitionResponse,
} from "@/lib/api/automations";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/settings/automations",
}));

const mockToggleRule = vi.fn().mockResolvedValue({ success: true });
const mockDeleteRule = vi.fn().mockResolvedValue({ success: true });
const mockActivateTemplate = vi.fn().mockResolvedValue({ success: true });

vi.mock("@/app/(app)/org/[slug]/settings/automations/actions", () => ({
  toggleRuleAction: (...args: unknown[]) => mockToggleRule(...args),
  deleteRuleAction: (...args: unknown[]) => mockDeleteRule(...args),
  activateTemplateAction: (...args: unknown[]) => mockActivateTemplate(...args),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const sampleRules: AutomationRuleResponse[] = [
  {
    id: "rule-1",
    name: "Auto-assign tasks",
    description: "Assign new tasks to the project lead",
    enabled: true,
    triggerType: "TASK_STATUS_CHANGED",
    triggerConfig: {},
    conditions: [],
    source: "MANUAL",
    templateSlug: null,
    createdBy: "user-1",
    createdAt: "2026-01-15T10:00:00Z",
    updatedAt: "2026-03-01T14:30:00Z",
    actions: [
      {
        id: "action-1",
        ruleId: "rule-1",
        sortOrder: 0,
        actionType: "ASSIGN_MEMBER",
        actionConfig: {},
        delayDuration: null,
        delayUnit: null,
        createdAt: "2026-01-15T10:00:00Z",
        updatedAt: "2026-01-15T10:00:00Z",
      },
    ],
  },
  {
    id: "rule-2",
    name: "Invoice notification",
    description: null,
    enabled: false,
    triggerType: "INVOICE_STATUS_CHANGED",
    triggerConfig: {},
    conditions: [],
    source: "TEMPLATE",
    templateSlug: "invoice-notify",
    createdBy: "user-1",
    createdAt: "2026-02-01T09:00:00Z",
    updatedAt: "2026-02-15T11:00:00Z",
    actions: [],
  },
];

const sampleTemplates: TemplateDefinitionResponse[] = [
  {
    slug: "task-assign",
    name: "Auto-Assign Tasks",
    description: "Automatically assign tasks when status changes",
    category: "Tasks",
    triggerType: "TASK_STATUS_CHANGED",
    triggerConfig: {},
    actionCount: 1,
  },
  {
    slug: "invoice-notify",
    name: "Invoice Notification",
    description: "Send notification when invoice status changes",
    category: "Billing",
    triggerType: "INVOICE_STATUS_CHANGED",
    triggerConfig: {},
    actionCount: 2,
  },
];

describe("RuleList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders rules in table", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    expect(screen.getByText("Auto-assign tasks")).toBeInTheDocument();
    expect(screen.getByText("Invoice notification")).toBeInTheDocument();
  });

  it("shows empty state when no rules", () => {
    render(
      <RuleList
        slug="acme"
        rules={[]}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    expect(screen.getByText("No automation rules yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Create your first automation or browse templates to get started.",
      ),
    ).toBeInTheDocument();
  });

  it("shows non-admin empty state message", () => {
    render(
      <RuleList
        slug="acme"
        rules={[]}
        templates={sampleTemplates}
        canManage={false}
      />,
    );
    expect(
      screen.getByText("No automation rules have been created yet."),
    ).toBeInTheDocument();
  });

  it("renders toggle switches for each rule", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    const toggles = screen.getAllByRole("switch");
    expect(toggles).toHaveLength(2);
    expect(toggles[0]).toBeChecked();
    expect(toggles[1]).not.toBeChecked();
  });

  it("renders trigger type badges", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    expect(screen.getByText("Task Status")).toBeInTheDocument();
    expect(screen.getByText("Invoice Status")).toBeInTheDocument();
  });

  it("navigates to rule detail on row click", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    fireEvent.click(screen.getByText("Auto-assign tasks"));
    expect(mockPush).toHaveBeenCalledWith(
      "/org/acme/settings/automations/rule-1",
    );
  });

  it("opens template gallery when Browse Templates is clicked", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    fireEvent.click(screen.getByText("Browse Templates"));
    expect(screen.getByText("Automation Templates")).toBeInTheDocument();
  });

  it("shows delete confirmation on first click", () => {
    render(
      <RuleList
        slug="acme"
        rules={sampleRules}
        templates={sampleTemplates}
        canManage={true}
      />,
    );
    const deleteButtons = screen.getAllByLabelText(/Delete/);
    expect(deleteButtons.length).toBeGreaterThan(0);
  });
});
