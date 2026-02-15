import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateViewDialog } from "@/components/views/CreateViewDialog";
import type { TagResponse, FieldDefinitionResponse } from "@/lib/types";

const allTags: TagResponse[] = [
  {
    id: "tag-1",
    name: "Urgent",
    slug: "urgent",
    color: "#FF0000",
    createdAt: "2025-01-15T10:00:00Z",
    updatedAt: "2025-01-15T10:00:00Z",
  },
  {
    id: "tag-2",
    name: "VIP",
    slug: "vip",
    color: "#FFD700",
    createdAt: "2025-01-15T10:00:00Z",
    updatedAt: "2025-01-15T10:00:00Z",
  },
];

const fieldDefs: FieldDefinitionResponse[] = [
  {
    id: "fd-1",
    entityType: "PROJECT",
    name: "Court",
    slug: "court",
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
    createdAt: "2025-01-15T10:00:00Z",
    updatedAt: "2025-01-15T10:00:00Z",
  },
];

describe("CreateViewDialog", () => {
  const mockOnSave = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockOnSave.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders step 1 with filter inputs when dialog opens", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Button</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Button"));

    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });
    expect(screen.getByText("Step 1 of 3")).toBeInTheDocument();
    expect(screen.getByText("Tags")).toBeInTheDocument();
    expect(screen.getByText("Date Range")).toBeInTheDocument();
    expect(screen.getByText("Search")).toBeInTheDocument();
  });

  it("navigates to step 2 with column checkboxes", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Nav</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Nav"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });
    expect(screen.getByText("Step 2 of 3")).toBeInTheDocument();
    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Description")).toBeInTheDocument();
    // Custom field column
    expect(screen.getByText("Court")).toBeInTheDocument();
  });

  it("navigates to step 3 with save form", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Save</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Save"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    // Navigate to step 2
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });

    // Navigate to step 3
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Name Your View")).toBeInTheDocument();
    });
    expect(screen.getByText("Step 3 of 3")).toBeInTheDocument();
    expect(screen.getByLabelText("View Name")).toBeInTheDocument();
    expect(screen.getByText("Share with team")).toBeInTheDocument();
  });

  it("calls onSave with correct payload on submission", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Submit</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Submit"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    // Step 1 → Step 2 → Step 3
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Name Your View")).toBeInTheDocument();
    });

    // Fill in name
    await user.type(screen.getByLabelText("View Name"), "My Test View");

    // Submit
    await user.click(screen.getByRole("button", { name: /Save View/i }));

    await waitFor(() => {
      expect(mockOnSave).toHaveBeenCalledWith(
        expect.objectContaining({
          entityType: "PROJECT",
          name: "My Test View",
          shared: false,
          sortOrder: 0,
        }),
      );
    });
  });

  it("shared checkbox disabled for non-admin users", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={false}
        onSave={mockOnSave}
      >
        <button>Create View NonAdmin</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View NonAdmin"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    // Navigate to step 3
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Name Your View")).toBeInTheDocument();
    });

    // Shared checkbox should be disabled
    const sharedCheckbox = screen.getByRole("checkbox");
    expect(sharedCheckbox).toBeDisabled();
    expect(screen.getByText("(admin/owner only)")).toBeInTheDocument();
  });

  it("shows Previous button on steps 2 and 3", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Previous</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Previous"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    // Step 1 should not have Previous button
    expect(screen.queryByText("Previous")).not.toBeInTheDocument();

    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });

    // Step 2 should have Previous button
    expect(screen.getByText("Previous")).toBeInTheDocument();

    // Go back to step 1
    await user.click(screen.getByText("Previous"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });
  });

  it("shows error when saving without a name", async () => {
    const user = userEvent.setup();

    render(
      <CreateViewDialog
        slug="acme"
        entityType="PROJECT"
        allTags={allTags}
        fieldDefinitions={fieldDefs}
        canCreateShared={true}
        onSave={mockOnSave}
      >
        <button>Create View Error</button>
      </CreateViewDialog>,
    );

    await user.click(screen.getByText("Create View Error"));
    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });

    // Navigate to step 3
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Select Columns")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Next"));
    await waitFor(() => {
      expect(screen.getByText("Name Your View")).toBeInTheDocument();
    });

    // Try to save without name
    await user.click(screen.getByRole("button", { name: /Save View/i }));

    await waitFor(() => {
      expect(screen.getByText("View name is required.")).toBeInTheDocument();
    });

    expect(mockOnSave).not.toHaveBeenCalled();
  });
});
