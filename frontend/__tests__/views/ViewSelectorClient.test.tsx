import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ViewSelectorClient } from "@/components/views/ViewSelectorClient";
import type { SavedViewResponse } from "@/lib/types";
import { DEFAULT_MATTER_VIEW_ID, MATTER_SYSTEM_VIEWS } from "@/lib/constants/system-views";

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
      />
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
      />
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
      />
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
      />
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
      />
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
      />
    );

    await user.click(screen.getByText("Save View"));

    await waitFor(() => {
      expect(screen.getByText("Configure Filters")).toBeInTheDocument();
    });
  });
  // 615C: optional system views; callers that omit them keep the "All" tab (tested above).
  describe("with system views", () => {
    function renderWithSystem() {
      return render(
        <ViewSelectorClient
          entityType="PROJECT"
          views={views}
          canCreate={true}
          canCreateShared={false}
          slug="acme"
          allTags={[]}
          fieldDefinitions={[]}
          onSave={mockOnSave}
          systemViews={MATTER_SYSTEM_VIEWS}
          defaultViewId={DEFAULT_MATTER_VIEW_ID}
        />
      );
    }

    afterEach(() => {
      mockSearchParams.delete("view");
      mockSearchParams.delete("status");
      mockSearchParams.delete("tags");
    });

    // F-3: the matters list ignores `tags` on the saved-view path and hides the filter there.
    describe("clearOnSavedView", () => {
      function renderClearing(clearOnSavedView?: readonly string[]) {
        return render(
          <ViewSelectorClient
            entityType="PROJECT"
            views={views}
            canCreate={false}
            canCreateShared={false}
            slug="acme"
            allTags={[]}
            fieldDefinitions={[]}
            onSave={mockOnSave}
            systemViews={MATTER_SYSTEM_VIEWS}
            defaultViewId={DEFAULT_MATTER_VIEW_ID}
            clearOnSavedView={clearOnSavedView}
          />
        );
      }

      it("drops the listed params when switching to a saved view", async () => {
        const user = userEvent.setup();
        mockSearchParams.set("tags", "urgent");
        mockSearchParams.set("status", "CLOSED");
        renderClearing(["tags"]);

        await user.click(screen.getByRole("tab", { name: "My View" }));

        expect(mockPush).toHaveBeenCalledWith("?status=CLOSED&view=view-2");
      });

      it("keeps the listed params when switching to a system view", async () => {
        const user = userEvent.setup();
        mockSearchParams.set("tags", "urgent");
        renderClearing(["tags"]);

        await user.click(screen.getByRole("tab", { name: "Dormant" }));

        expect(mockPush).toHaveBeenCalledWith("?tags=urgent&view=system%3Adormant");
      });

      it("keeps every param on a saved view when the prop is omitted (other callers)", async () => {
        const user = userEvent.setup();
        mockSearchParams.set("tags", "urgent");
        renderClearing();

        await user.click(screen.getByRole("tab", { name: "My View" }));

        expect(mockPush).toHaveBeenCalledWith("?tags=urgent&view=view-2");
      });
    });

    it("pushes ?view=system:<slug> when a system tab is clicked, keeping other params", async () => {
      const user = userEvent.setup();
      mockSearchParams.set("status", "CLOSED");
      renderWithSystem();

      await user.click(screen.getByRole("tab", { name: "Dormant" }));

      expect(mockPush).toHaveBeenCalledWith("?status=CLOSED&view=system%3Adormant");
    });

    it("defaults to Open matters and has no All tab", () => {
      renderWithSystem();
      expect(screen.queryByText("All")).not.toBeInTheDocument();
      expect(screen.getByRole("tab", { name: "Open matters" })).toHaveAttribute(
        "data-state",
        "active"
      );
    });

    it("Save View from a system view opens the dialog pre-filled with its params (F-4)", async () => {
      const user = userEvent.setup();
      mockSearchParams.set("view", "system:awaiting-fee-note");
      renderWithSystem();

      await user.click(screen.getByText("Save View"));

      const carried = await screen.findByTestId("carried-filters");
      expect(carried).toHaveTextContent("Carried from Awaiting fee note");
      expect(carried).toHaveTextContent("Status: Active");
      expect(carried).toHaveTextContent("Has unbilled time");
    });

    it("Save View from a saved view carries nothing", async () => {
      const user = userEvent.setup();
      mockSearchParams.set("view", "view-2");
      renderWithSystem();

      await user.click(screen.getByText("Save View"));

      await screen.findByText("Configure Filters");
      expect(screen.queryByTestId("carried-filters")).not.toBeInTheDocument();
    });
  });
});
