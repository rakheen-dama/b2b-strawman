import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { RuleList } from "@/components/automations/rule-list";
import type { AutomationRuleResponse } from "@/lib/api/automations";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/settings/automations",
}));

const mockToggleRule = vi.fn().mockResolvedValue({ success: true });

vi.mock("@/app/(app)/org/[slug]/settings/automations/actions", () => ({
  toggleRuleAction: (...args: unknown[]) => mockToggleRule(...args),
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

describe("RuleList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders rules in table", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={true} />);
    expect(screen.getByText("Auto-assign tasks")).toBeInTheDocument();
    expect(screen.getByText("Invoice notification")).toBeInTheDocument();
  });

  it("shows empty state when no rules", () => {
    render(<RuleList slug="acme" rules={[]} canManage={true} />);
    expect(screen.getByText("No automation rules yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Automation rules are installed with your practice packs and will appear here."
      )
    ).toBeInTheDocument();
  });

  it("renders toggle switches for each rule", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={true} />);
    const toggles = screen.getAllByRole("switch");
    expect(toggles).toHaveLength(2);
    expect(toggles[0]).toBeChecked();
    expect(toggles[1]).not.toBeChecked();
  });

  it("calls toggleRuleAction when a switch is flipped", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={true} />);
    fireEvent.click(screen.getByLabelText("Toggle Auto-assign tasks"));
    expect(mockToggleRule).toHaveBeenCalledWith("acme", "rule-1");
  });

  it("disables toggles for non-managers", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={false} />);
    for (const toggle of screen.getAllByRole("switch")) {
      expect(toggle).toBeDisabled();
    }
  });

  it("renders trigger type badges", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={true} />);
    expect(screen.getByText("Task Status")).toBeInTheDocument();
    expect(screen.getByText("Invoice Status")).toBeInTheDocument();
  });

  // Phase 86 (ADR-334): the rule editor, template gallery and delete were removed — the list is
  // enable/disable only.
  it("renders rule names as plain text with no editor, gallery, or delete affordances", () => {
    render(<RuleList slug="acme" rules={sampleRules} canManage={true} />);
    expect(screen.getByText("Auto-assign tasks").closest("a")).toBeNull();
    expect(screen.queryByText("New Automation")).not.toBeInTheDocument();
    expect(screen.queryByText("Browse Templates")).not.toBeInTheDocument();
    expect(screen.queryAllByLabelText(/Delete/)).toHaveLength(0);
  });
});
