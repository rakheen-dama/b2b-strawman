import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import type { SavedViewResponse } from "@/lib/types";

// Mock next/navigation
const mockPush = vi.fn();
const mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
  useSearchParams: () => mockSearchParams,
}));

const sharedView: SavedViewResponse = {
  id: "view-1",
  entityType: "PROJECT",
  name: "Active",
  filters: { tags: ["active"] },
  columns: ["name"],
  shared: true,
  createdBy: "user-1",
  sortOrder: 0,
  createdAt: "2025-06-01T10:00:00Z",
  updatedAt: "2025-06-01T10:00:00Z",
};

const personalView: SavedViewResponse = {
  id: "view-2",
  entityType: "PROJECT",
  name: "My View",
  filters: {},
  columns: null,
  shared: false,
  createdBy: "user-1",
  sortOrder: 1,
  createdAt: "2025-06-02T10:00:00Z",
  updatedAt: "2025-06-02T10:00:00Z",
};

const views = [sharedView, personalView];

const mockOnSave = vi.fn().mockResolvedValue({ success: true });

describe("ViewSelectorClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset search params to no view selected
    mockSearchParams.delete("view");
  });

  afterEach(() => {
    cleanup();
  });

  it("renders view selector with all views", () => {
    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={true}
        canCreateShared={true}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    expect(screen.getByText("All")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("My View")).toBeInTheDocument();
  });

  it("updates URL when clicking a view tab", async () => {
    const user = userEvent.setup();

    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={false}
        canCreateShared={false}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    await user.click(screen.getByText("Active"));

    expect(mockPush).toHaveBeenCalledWith("?view=view-1");
  });

  it("clears URL when clicking All tab", async () => {
    const user = userEvent.setup();

    // Simulate having a view selected
    mockSearchParams.set("view", "view-1");

    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={false}
        canCreateShared={false}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    await user.click(screen.getByText("All"));

    expect(mockPush).toHaveBeenCalledWith("?");
  });

  it("shows Save View button when canCreate is true", () => {
    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={true}
        canCreateShared={true}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    expect(screen.getByText("Save View")).toBeInTheDocument();
  });

  it("does not show Save View button when canCreate is false", () => {
    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={false}
        canCreateShared={false}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    expect(screen.queryByText("Save View")).not.toBeInTheDocument();
  });

  it("opens create dialog when Save View is clicked", async () => {
    const user = userEvent.setup();

    render(
      <ViewSelectorClient
        entityType="PROJECT"
        views={views}
        canCreate={true}
        canCreateShared={true}
        slug="acme"
        allTags={[]}
        fieldDefinitions={[]}
        onSave={mockOnSave}
      />,
    );

    await user.click(screen.getByText("Save View"));

    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });
  });
});
