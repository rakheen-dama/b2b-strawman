import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { ActionList, type ActionRow } from "@/components/automations/action-list";

// Ensure crypto.randomUUID is available in test environment
if (!globalThis.crypto?.randomUUID) {
  let counter = 0;
  Object.defineProperty(globalThis, "crypto", {
    value: {
      ...globalThis.crypto,
      randomUUID: () => `action-test-uuid-${++counter}`,
    },
  });
}

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/org/acme/settings/automations/new",
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("ActionList & ActionForm", () => {
  const mockOnActionsChange = vi.fn();

  const defaultProps = {
    actions: [] as ActionRow[],
    onActionsChange: mockOnActionsChange,
    triggerType: "TASK_STATUS_CHANGED" as const,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows empty state and Add Action button", () => {
    render(<ActionList {...defaultProps} />);

    expect(
      screen.getByText(
        "No actions configured. Add an action to define what happens when this rule triggers."
      )
    ).toBeInTheDocument();
    expect(screen.getByText("Add Action")).toBeInTheDocument();
  });

  it("shows action type selector when Add Action is clicked", () => {
    render(<ActionList {...defaultProps} />);

    fireEvent.click(screen.getByText("Add Action"));

    // Should show the select trigger with placeholder
    expect(screen.getByText("Select action type")).toBeInTheDocument();
    // Should show a cancel button
    expect(screen.getByText("Cancel")).toBeInTheDocument();
  });

  it("renders CREATE_TASK form fields when a CREATE_TASK action exists", () => {
    const actions: ActionRow[] = [
      {
        id: "a-1",
        actionType: "CREATE_TASK",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: null,
        delayUnit: null,
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    expect(screen.getByText("Create Task")).toBeInTheDocument();
    expect(screen.getByLabelText("Task Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Task Description")).toBeInTheDocument();
    // Assign To select should be present
    expect(screen.getByText("Assign To")).toBeInTheDocument();
  });

  it("renders SEND_NOTIFICATION form fields", () => {
    const actions: ActionRow[] = [
      {
        id: "a-2",
        actionType: "SEND_NOTIFICATION",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: null,
        delayUnit: null,
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    expect(screen.getByText("Send Notification")).toBeInTheDocument();
    expect(screen.getByText("Recipient")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toBeInTheDocument();
  });

  it("shows Insert Variable buttons for text fields in notification action", () => {
    const actions: ActionRow[] = [
      {
        id: "a-3",
        actionType: "SEND_NOTIFICATION",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: null,
        delayUnit: null,
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    // Should have Insert Variable buttons for title and message fields
    const insertButtons = screen.getAllByText("Insert Variable");
    expect(insertButtons.length).toBeGreaterThanOrEqual(2);
  });

  it("shows delay duration and unit inputs when delay toggle is enabled", () => {
    const actions: ActionRow[] = [
      {
        id: "a-4",
        actionType: "CREATE_TASK",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: 2,
        delayUnit: "HOURS",
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    // Delay indicator should show in header
    expect(screen.getByText("2 hours")).toBeInTheDocument();

    // Duration input should be visible
    expect(screen.getByLabelText("Delay duration")).toBeInTheDocument();
    expect(screen.getByDisplayValue("2")).toBeInTheDocument();
  });

  it("removes an action when remove button is clicked", () => {
    const actions: ActionRow[] = [
      {
        id: "a-5",
        actionType: "CREATE_TASK",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: null,
        delayUnit: null,
      },
      {
        id: "a-6",
        actionType: "SEND_EMAIL",
        actionConfig: {},
        sortOrder: 1,
        delayDuration: null,
        delayUnit: null,
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    const removeButtons = screen.getAllByLabelText("Remove action");
    expect(removeButtons).toHaveLength(2);

    // Remove the first action
    fireEvent.click(removeButtons[0]);

    // onActionsChange should be called with just the second action
    expect(mockOnActionsChange).toHaveBeenCalledWith([
      expect.objectContaining({ id: "a-6", sortOrder: 0 }),
    ]);
  });

  it("reorders actions when up/down buttons are clicked", () => {
    const actions: ActionRow[] = [
      {
        id: "a-7",
        actionType: "CREATE_TASK",
        actionConfig: {},
        sortOrder: 0,
        delayDuration: null,
        delayUnit: null,
      },
      {
        id: "a-8",
        actionType: "SEND_EMAIL",
        actionConfig: {},
        sortOrder: 1,
        delayDuration: null,
        delayUnit: null,
      },
    ];

    render(<ActionList {...defaultProps} actions={actions} />);

    // Move second action up
    const moveUpButtons = screen.getAllByLabelText("Move action up");
    fireEvent.click(moveUpButtons[1]); // Click the second action's "up" button

    expect(mockOnActionsChange).toHaveBeenCalledWith([
      expect.objectContaining({ id: "a-8", sortOrder: 0 }),
      expect.objectContaining({ id: "a-7", sortOrder: 1 }),
    ]);
  });
});
