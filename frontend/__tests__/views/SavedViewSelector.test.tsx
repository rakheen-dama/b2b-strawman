import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SavedViewSelector } from "@/components/views/SavedViewSelector";
import type { SavedViewResponse } from "@/lib/types";

const sharedView1: SavedViewResponse = {
  id: "view-1",
  entityType: "PROJECT",
  name: "Active Projects",
  filters: { tags: ["active"] },
  columns: ["name", "status"],
  shared: true,
  createdBy: "user-1",
  sortOrder: 0,
  createdAt: "2025-06-01T10:00:00Z",
  updatedAt: "2025-06-01T10:00:00Z",
};

const sharedView2: SavedViewResponse = {
  id: "view-2",
  entityType: "PROJECT",
  name: "VIP Clients",
  filters: { tags: ["vip"] },
  columns: ["name", "email"],
  shared: true,
  createdBy: "user-1",
  sortOrder: 1,
  createdAt: "2025-06-02T10:00:00Z",
  updatedAt: "2025-06-02T10:00:00Z",
};

const personalView: SavedViewResponse = {
  id: "view-3",
  entityType: "PROJECT",
  name: "My Queue",
  filters: { search: "assigned" },
  columns: null,
  shared: false,
  createdBy: "user-2",
  sortOrder: 2,
  createdAt: "2025-06-03T10:00:00Z",
  updatedAt: "2025-06-03T10:00:00Z",
};

const allViews = [sharedView1, sharedView2, personalView];

describe("SavedViewSelector", () => {
  const mockOnViewChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders All tab and tabs for each view", () => {
    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId={null}
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    expect(screen.getByText("All")).toBeInTheDocument();
    expect(screen.getByText("Active Projects")).toBeInTheDocument();
    expect(screen.getByText("VIP Clients")).toBeInTheDocument();
    expect(screen.getByText("My Queue")).toBeInTheDocument();
  });

  it("shows (shared) indicator for shared views", () => {
    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId={null}
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    // shared views have "(shared)" text
    const sharedIndicators = screen.getAllByText("(shared)");
    expect(sharedIndicators).toHaveLength(2);
  });

  it("highlights active tab based on currentViewId", () => {
    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId="view-2"
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    // The "VIP Clients" tab should be the active one
    const vipTab = screen.getByText("VIP Clients").closest("[data-slot='tabs-trigger']");
    expect(vipTab).toHaveAttribute("data-state", "active");

    // "All" tab should not be active
    const allTab = screen.getByText("All").closest("[data-slot='tabs-trigger']");
    expect(allTab).toHaveAttribute("data-state", "inactive");
  });

  it("calls onViewChange with null when clicking All tab", async () => {
    const user = userEvent.setup();

    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId="view-1"
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    await user.click(screen.getByText("All"));

    expect(mockOnViewChange).toHaveBeenCalledWith(null);
  });

  it("calls onViewChange with view id when clicking a view tab", async () => {
    const user = userEvent.setup();

    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId={null}
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    await user.click(screen.getByText("My Queue"));

    expect(mockOnViewChange).toHaveBeenCalledWith("view-3");
  });

  it("does not show Save View button when canCreate is false", () => {
    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId={null}
        onViewChange={mockOnViewChange}
        canCreate={false}
      />,
    );

    expect(screen.queryByText("Save View")).not.toBeInTheDocument();
  });

  it("shows Save View button when canCreate is true", () => {
    const mockOnCreateClick = vi.fn();

    render(
      <SavedViewSelector
        entityType="PROJECT"
        views={allViews}
        currentViewId={null}
        onViewChange={mockOnViewChange}
        canCreate={true}
        onCreateClick={mockOnCreateClick}
      />,
    );

    expect(screen.getByText("Save View")).toBeInTheDocument();
  });
});
