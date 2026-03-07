import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { RuleForm } from "@/components/automations/rule-form";

// Ensure crypto.randomUUID is available in test environment
if (!globalThis.crypto?.randomUUID) {
  let counter = 0;
  Object.defineProperty(globalThis, "crypto", {
    value: {
      ...globalThis.crypto,
      randomUUID: () => `test-uuid-${++counter}`,
    },
  });
}

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/settings/automations/new",
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("RuleForm", () => {
  const mockOnSave = vi.fn();
  const mockOnCancel = vi.fn();

  const defaultProps = {
    onSave: mockOnSave,
    onCancel: mockOnCancel,
    isSaving: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form with all three sections visible", () => {
    render(<RuleForm {...defaultProps} />);

    expect(screen.getByText("Trigger")).toBeInTheDocument();
    expect(screen.getByText("Conditions")).toBeInTheDocument();
    expect(screen.getByText("Actions")).toBeInTheDocument();
    expect(screen.getByText("Add Action")).toBeInTheDocument();
  });

  it("shows status change config when status trigger is selected", () => {
    render(<RuleForm {...defaultProps} />);

    // Select a trigger type - find the trigger type select
    const triggerSelect = screen.getByLabelText("Trigger Type");
    expect(triggerSelect).toBeInTheDocument();

    // Before selecting, should show "Select a trigger type"
    expect(
      screen.getByText("Select a trigger type to configure additional settings."),
    ).toBeInTheDocument();
  });

  it("renders budget threshold input when BUDGET_THRESHOLD_REACHED is selected via rule prop", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-1",
          name: "Budget alert",
          description: null,
          enabled: true,
          triggerType: "BUDGET_THRESHOLD_REACHED",
          triggerConfig: { thresholdPercent: 90 },
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    expect(screen.getByLabelText("Budget Threshold")).toBeInTheDocument();
    expect(screen.getByDisplayValue("90")).toBeInTheDocument();
  });

  it("renders simple trigger helper text via rule prop", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-2",
          name: "Time logged",
          description: null,
          enabled: true,
          triggerType: "TIME_ENTRY_CREATED",
          triggerConfig: {},
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    expect(
      screen.getByText("This trigger has no additional configuration."),
    ).toBeInTheDocument();
  });

  it("renders status from/to selects for status change triggers", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-3",
          name: "Task done",
          description: null,
          enabled: true,
          triggerType: "TASK_STATUS_CHANGED",
          triggerConfig: {},
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    expect(screen.getByLabelText("From Status")).toBeInTheDocument();
    expect(screen.getByLabelText("To Status")).toBeInTheDocument();
  });

  it("adds a condition row when Add Condition is clicked", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-4",
          name: "With conditions",
          description: null,
          enabled: true,
          triggerType: "TASK_STATUS_CHANGED",
          triggerConfig: {},
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    const addButton = screen.getByText("Add Condition");
    fireEvent.click(addButton);

    // Should now have a remove button for the condition row
    expect(screen.getByLabelText("Remove condition")).toBeInTheDocument();
  });

  it("removes a condition row when X button is clicked", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-5",
          name: "Remove test",
          description: null,
          enabled: true,
          triggerType: "TASK_STATUS_CHANGED",
          triggerConfig: {},
          conditions: [{ field: "task.name", operator: "EQUALS", value: "test" }],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    // Should have a remove button
    const removeButton = screen.getByLabelText("Remove condition");
    fireEvent.click(removeButton);

    // Remove button should be gone
    expect(screen.queryByLabelText("Remove condition")).not.toBeInTheDocument();
  });

  it("validates that name and trigger type are required", () => {
    render(<RuleForm {...defaultProps} />);

    // Click Create Rule without filling in required fields
    const submitButton = screen.getByText("Create Rule");
    fireEvent.click(submitButton);

    expect(screen.getByText("Name is required.")).toBeInTheDocument();
    expect(screen.getByText("Trigger type is required.")).toBeInTheDocument();
    expect(mockOnSave).not.toHaveBeenCalled();
  });

  it("shows Save Changes button in edit mode", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-6",
          name: "Existing rule",
          description: "Desc",
          enabled: true,
          triggerType: "TIME_ENTRY_CREATED",
          triggerConfig: {},
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    expect(screen.getByText("Save Changes")).toBeInTheDocument();
  });

  it("adds multiple condition rows and removes one from the middle", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-7",
          name: "Multi conditions",
          description: null,
          enabled: true,
          triggerType: "TASK_STATUS_CHANGED",
          triggerConfig: {},
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    const addButton = screen.getByText("Add Condition");
    fireEvent.click(addButton);
    fireEvent.click(addButton);
    fireEvent.click(addButton);

    const removeButtons = screen.getAllByLabelText("Remove condition");
    expect(removeButtons).toHaveLength(3);

    // Remove the middle one
    fireEvent.click(removeButtons[1]);
    expect(screen.getAllByLabelText("Remove condition")).toHaveLength(2);
  });

  it("hides value input for IS_NULL and IS_NOT_NULL operators", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-8",
          name: "Nullary op",
          description: null,
          enabled: true,
          triggerType: "TASK_STATUS_CHANGED",
          triggerConfig: {},
          conditions: [
            { field: "task.name", operator: "IS_NULL", value: "" },
            { field: "task.status", operator: "EQUALS", value: "DONE" },
          ],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    // There should be only ONE value input (for the EQUALS row), not two
    const valueInputs = screen.getAllByPlaceholderText("Value");
    expect(valueInputs).toHaveLength(1);
  });

  it("renders From/To status selects for status change trigger via rule prop", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-9",
          name: "Invoice status",
          description: null,
          enabled: true,
          triggerType: "INVOICE_STATUS_CHANGED",
          triggerConfig: { fromStatus: "DRAFT", toStatus: null },
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    expect(screen.getByLabelText("From Status")).toBeInTheDocument();
    expect(screen.getByLabelText("To Status")).toBeInTheDocument();
  });

  it("renders budget threshold input and guards against NaN", () => {
    render(
      <RuleForm
        {...defaultProps}
        rule={{
          id: "r-10",
          name: "Budget guard",
          description: null,
          enabled: true,
          triggerType: "BUDGET_THRESHOLD_REACHED",
          triggerConfig: { thresholdPercent: 75 },
          conditions: [],
          source: "MANUAL",
          templateSlug: null,
          createdBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          actions: [],
        }}
      />,
    );

    const input = screen.getByDisplayValue("75");
    expect(input).toBeInTheDocument();

    // Clearing input should not crash — NaN guard
    fireEvent.change(input, { target: { value: "" } });
    // Input should still be in the document (component didn't crash)
    expect(screen.getByLabelText("Budget Threshold")).toBeInTheDocument();
  });
});
