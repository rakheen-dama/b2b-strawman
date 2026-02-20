import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaskDetailSheet } from "@/components/tasks/task-detail-sheet";
import type {
  Task,
  TagResponse,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

// --- Mocks ---

const mockFetchTask = vi.fn();
const mockUpdateTask = vi.fn();
const mockFetchTimeEntries = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  fetchTask: (...args: unknown[]) => mockFetchTask(...args),
  updateTask: (...args: unknown[]) => mockUpdateTask(...args),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  fetchTimeEntries: (...args: unknown[]) => mockFetchTimeEntries(...args),
  updateTimeEntry: vi.fn(),
  deleteTimeEntry: vi.fn(),
}));

vi.mock("server-only", () => ({}));

vi.mock("@/lib/actions/comments", () => ({
  fetchComments: vi.fn().mockResolvedValue([]),
  createComment: vi.fn().mockResolvedValue({ success: true }),
  updateComment: vi.fn().mockResolvedValue({ success: true }),
  deleteComment: vi.fn().mockResolvedValue({ success: true }),
}));

const mockSetEntityTags = vi.fn();
const mockUpdateEntityCustomFields = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/tags/actions", () => ({
  createTagAction: vi.fn(),
  setEntityTagsAction: (...args: unknown[]) => mockSetEntityTags(...args),
}));

vi.mock("@/app/(app)/org/[slug]/settings/custom-fields/actions", () => ({
  updateEntityCustomFieldsAction: (...args: unknown[]) =>
    mockUpdateEntityCustomFields(...args),
}));

// --- Test data ---

const tag1: TagResponse = {
  id: "tag-1",
  name: "Urgent",
  slug: "urgent",
  color: "#FF0000",
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const tag2: TagResponse = {
  id: "tag-2",
  name: "Bug",
  slug: "bug",
  color: null,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const textFieldDef: FieldDefinitionResponse = {
  id: "fd-1",
  entityType: "TASK",
  name: "Sprint Number",
  slug: "sprint_number",
  fieldType: "TEXT",
  description: null,
  required: false,
  defaultValue: null,
  options: null,
  validation: null,
  sortOrder: 0,
  packId: null,
  packFieldKey: null,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const testFieldGroup: FieldGroupResponse = {
  id: "grp-1",
  entityType: "TASK",
  name: "Sprint Fields",
  slug: "sprint_fields",
  description: null,
  packId: null,
  sortOrder: 0,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const testGroupMembers: FieldGroupMemberResponse[] = [
  {
    id: "gm-1",
    fieldGroupId: "grp-1",
    fieldDefinitionId: "fd-1",
    sortOrder: 0,
  },
];

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: "t1",
    projectId: "p1",
    title: "Fix login bug",
    description: "Steps to reproduce the issue.",
    status: "IN_PROGRESS",
    priority: "HIGH",
    type: "BUG",
    assigneeId: null,
    assigneeName: null,
    createdBy: "m1",
    createdByName: "Alice",
    dueDate: "2026-03-15",
    version: 1,
    createdAt: "2026-01-10T10:00:00Z",
    updatedAt: "2026-02-20T08:00:00Z",
    tags: [],
    customFields: {},
    appliedFieldGroups: [],
    ...overrides,
  };
}

const defaultProps = {
  projectId: "p1",
  slug: "acme",
  canManage: true,
  currentMemberId: "current-member",
  orgRole: "org:member",
  members: [
    { id: "m1", name: "Alice", email: "alice@example.com" },
    { id: "m2", name: "Bob", email: "bob@example.com" },
  ],
  onClose: vi.fn(),
  allTags: [tag1, tag2],
  fieldDefinitions: [textFieldDef],
  fieldGroups: [testFieldGroup],
  groupMembers: { "grp-1": testGroupMembers },
};

describe("TaskDetailSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTimeEntries.mockResolvedValue([]);
    mockFetchTask.mockResolvedValue(makeTask());
    mockUpdateTask.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup(); // REQUIRED: Radix Sheet is built on Dialog, leaks DOM between tests
  });

  // Test 1: Sheet renders when taskId provided
  it("renders with task title, status badge, and priority badge when taskId is provided", async () => {
    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    expect(mockFetchTask).toHaveBeenCalledWith("t1");
    expect(screen.getByText("In Progress")).toBeInTheDocument();
    expect(screen.getByText("High")).toBeInTheDocument();
  });

  // Test 2: Sheet is closed when taskId is null
  it("does not show sheet content when taskId is null", () => {
    render(<TaskDetailSheet {...defaultProps} taskId={null} />);

    // When taskId is null, Sheet open=false; task fetch never called
    expect(mockFetchTask).not.toHaveBeenCalled();
    // Task title should not be visible
    expect(screen.queryByRole("heading", { name: "Fix login bug" })).not.toBeInTheDocument();
  });

  // Test 3: Assignee change calls update endpoint
  it("calls updateTask with new assigneeId when assignee is changed", async () => {
    const user = userEvent.setup();

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Open the assignee selector (combobox) — multiple comboboxes now exist (status + assignee)
    const comboboxes = screen.getAllByRole("combobox");
    const assigneeButton = comboboxes.find((el) => el.textContent?.includes("Unassigned"));
    expect(assigneeButton).toBeDefined();
    await user.click(assigneeButton!);

    // Select "Alice" from the dropdown — use the option role to avoid the "Created By: Alice" text
    const aliceOption = await screen.findByRole("option", { name: /Alice/ });
    await user.click(aliceOption);

    await waitFor(() => {
      expect(mockUpdateTask).toHaveBeenCalledWith(
        "acme",
        "t1",
        "p1",
        expect.objectContaining({ assigneeId: "m1" }),
      );
    });
  });

  // Test 3b: Status change calls update endpoint
  it("calls updateTask with new status when status is changed via select", async () => {
    const user = userEvent.setup();

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Open the status select (find the combobox with current status text)
    const comboboxes = screen.getAllByRole("combobox");
    const statusSelect = comboboxes.find((el) => el.textContent?.includes("In Progress"));
    expect(statusSelect).toBeDefined();
    await user.click(statusSelect!);

    // Select "Done" from the dropdown
    const doneOption = await screen.findByRole("option", { name: /Done/ });
    await user.click(doneOption);

    await waitFor(() => {
      expect(mockUpdateTask).toHaveBeenCalledWith(
        "acme",
        "t1",
        "p1",
        expect.objectContaining({ status: "DONE" }),
      );
    });
  });

  // Test 3c: Status select not shown when user cannot manage
  it("shows read-only status badge when canManage is false and not own task", async () => {
    render(
      <TaskDetailSheet
        {...defaultProps}
        taskId="t1"
        canManage={false}
        currentMemberId="different-member"
      />,
    );

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Status should be a read-only badge, not a combobox select
    // Only the assignee combobox should exist (disabled), no status select
    const comboboxes = screen.queryAllByRole("combobox");
    const statusCombobox = comboboxes.find((el) => el.textContent?.includes("In Progress"));
    expect(statusCombobox).toBeUndefined();
    // The badge should still show
    expect(screen.getByText("In Progress")).toBeInTheDocument();
  });

  // Test 4: Time Entries tab shows TimeEntryList
  it("shows TimeEntryList in the Time Entries tab", async () => {
    mockFetchTimeEntries.mockResolvedValue([]);

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // "Time Entries" tab trigger should be present and active by default
    expect(
      screen.getByRole("tab", { name: "Time Entries" }),
    ).toBeInTheDocument();
    // TimeEntryList renders its empty state
    await waitFor(() => {
      expect(screen.getByText("No time logged yet")).toBeInTheDocument();
    });
  });

  // Test 5: Comments tab shows CommentSectionClient
  it("shows CommentSectionClient after clicking the Comments tab", async () => {
    const user = userEvent.setup();

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // Click the Comments tab
    const commentsTab = screen.getByRole("tab", { name: "Comments" });
    await user.click(commentsTab);

    // CommentSectionClient renders its content — AddCommentForm has this label
    await waitFor(() => {
      expect(screen.getByLabelText("Add a comment")).toBeInTheDocument();
    });
  });

  // Test 6: TagInput renders in sheet with current task tags
  it("renders TagInput with current task tags when task has tags", async () => {
    mockFetchTask.mockResolvedValue(
      makeTask({ tags: [tag1], appliedFieldGroups: [] }),
    );

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // TagInput renders with the "Urgent" tag badge
    expect(screen.getByText("Urgent")).toBeInTheDocument();
    // "Add Tag" button present (canManage=true)
    expect(screen.getByRole("button", { name: /Add Tag/i })).toBeInTheDocument();
  });

  // Test 7: CustomFieldSection renders in sheet with current field values
  it("renders CustomFieldSection when task has applied field groups", async () => {
    mockFetchTask.mockResolvedValue(
      makeTask({
        tags: [],
        appliedFieldGroups: ["grp-1"],
        customFields: { sprint_number: "Sprint 42" },
      }),
    );

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Fix login bug" })).toBeInTheDocument();
    });

    // CustomFieldSection shows group name and field
    expect(screen.getByText("Sprint Fields")).toBeInTheDocument();
    expect(screen.getByLabelText(/Sprint Number/)).toBeInTheDocument();
  });

  // Test 8: Tag change calls setEntityTagsAction
  it("calls setEntityTagsAction when a tag is removed", async () => {
    const user = userEvent.setup();
    mockSetEntityTags.mockResolvedValue({ success: true });
    mockFetchTask.mockResolvedValue(
      makeTask({ tags: [tag1, tag2], appliedFieldGroups: [] }),
    );

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Remove Urgent/i })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Remove Urgent/i }));

    await waitFor(() => {
      expect(mockSetEntityTags).toHaveBeenCalledWith(
        "acme",
        "TASK",
        "t1",
        ["tag-2"],
      );
    });
  });

  // Test 9: Custom field change calls updateEntityCustomFieldsAction
  it("calls updateEntityCustomFieldsAction when custom fields are saved", async () => {
    const user = userEvent.setup();
    mockUpdateEntityCustomFields.mockResolvedValue({ success: true });
    mockFetchTask.mockResolvedValue(
      makeTask({
        tags: [],
        appliedFieldGroups: ["grp-1"],
        customFields: {},
      }),
    );

    render(<TaskDetailSheet {...defaultProps} taskId="t1" />);

    await waitFor(() => {
      expect(screen.getByLabelText(/Sprint Number/)).toBeInTheDocument();
    });

    const sprintInput = screen.getByLabelText(/Sprint Number/);
    await user.type(sprintInput, "42");

    await user.click(screen.getByRole("button", { name: /Save Custom Fields/i }));

    await waitFor(() => {
      expect(mockUpdateEntityCustomFields).toHaveBeenCalledWith(
        "acme",
        "TASK",
        "t1",
        expect.objectContaining({ sprint_number: "42" }),
      );
    });
  });
});
