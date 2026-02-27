import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClausesContent } from "@/components/clauses/clauses-content";
import type { Clause } from "@/lib/actions/clause-actions";

const mockCreateClause = vi.fn();
const mockUpdateClause = vi.fn();
const mockCloneClause = vi.fn();
const mockDeactivateClause = vi.fn();
const mockDeleteClause = vi.fn();

vi.mock("@/lib/actions/clause-actions", () => ({
  createClause: (...args: unknown[]) => mockCreateClause(...args),
  updateClause: (...args: unknown[]) => mockUpdateClause(...args),
  cloneClause: (...args: unknown[]) => mockCloneClause(...args),
  deactivateClause: (...args: unknown[]) => mockDeactivateClause(...args),
  deleteClause: (...args: unknown[]) => mockDeleteClause(...args),
}));

const SYSTEM_CLAUSE: Clause = {
  id: "c-1",
  title: "Standard NDA",
  slug: "standard-nda",
  description: "Non-disclosure agreement clause",
  body: "<p>NDA body</p>",
  category: "Confidentiality",
  source: "SYSTEM",
  sourceClauseId: null,
  packId: "common-pack",
  active: true,
  sortOrder: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CUSTOM_CLAUSE: Clause = {
  id: "c-2",
  title: "Custom Liability",
  slug: "custom-liability",
  description: "Liability limitation clause",
  body: "<p>Liability body</p>",
  category: "Legal",
  source: "CUSTOM",
  sourceClauseId: null,
  packId: null,
  active: true,
  sortOrder: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CLONED_CLAUSE: Clause = {
  id: "c-3",
  title: "Cloned NDA",
  slug: "cloned-nda",
  description: null,
  body: "<p>Cloned NDA body</p>",
  category: "Confidentiality",
  source: "CLONED",
  sourceClauseId: "c-1",
  packId: "common-pack",
  active: false,
  sortOrder: 2,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const ALL_CLAUSES = [SYSTEM_CLAUSE, CUSTOM_CLAUSE, CLONED_CLAUSE];
const ALL_CATEGORIES = ["Confidentiality", "Legal"];

describe("ClausesContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateClause.mockResolvedValue({ success: true });
    mockUpdateClause.mockResolvedValue({ success: true });
    mockCloneClause.mockResolvedValue({ success: true });
    mockDeactivateClause.mockResolvedValue({ success: true });
    mockDeleteClause.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clause list grouped by category", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Both category headers should be present
    const headings = screen.getAllByRole("heading", { level: 2 });
    const headingTexts = headings.map((h) => h.textContent);
    expect(headingTexts).toContain("Confidentiality");
    expect(headingTexts).toContain("Legal");

    // All clause titles should be present
    expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    expect(screen.getByText("Custom Liability")).toBeInTheDocument();
    expect(screen.getByText("Cloned NDA")).toBeInTheDocument();
  });

  it("search filters by title", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    const searchInput = screen.getByPlaceholderText("Search clauses...");
    await user.type(searchInput, "Liability");

    expect(screen.getByText("Custom Liability")).toBeInTheDocument();
    expect(screen.queryByText("Standard NDA")).not.toBeInTheDocument();
    expect(screen.queryByText("Cloned NDA")).not.toBeInTheDocument();
  });

  it("category filter filters list", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Click the Shadcn Select trigger
    const selectTrigger = screen.getByLabelText("Filter by category");
    await user.click(selectTrigger);

    // Click the "Legal" option in the dropdown
    const legalOption = await screen.findByRole("option", { name: "Legal" });
    await user.click(legalOption);

    await waitFor(() => {
      expect(screen.getByText("Custom Liability")).toBeInTheDocument();
      expect(screen.queryByText("Standard NDA")).not.toBeInTheDocument();
      expect(screen.queryByText("Cloned NDA")).not.toBeInTheDocument();
    });
  });

  it("system clause shows clone only", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={[SYSTEM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    expect(screen.getByTitle("Clone clause")).toBeInTheDocument();
    expect(screen.queryByTitle("Edit clause")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Deactivate clause")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Delete clause")).not.toBeInTheDocument();
  });

  it("active custom clause shows edit deactivate and delete", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    expect(screen.getByTitle("Edit clause")).toBeInTheDocument();
    expect(screen.getByTitle("Deactivate clause")).toBeInTheDocument();
    expect(screen.getByTitle("Delete clause")).toBeInTheDocument();
    expect(screen.queryByTitle("Clone clause")).not.toBeInTheDocument();
  });

  it("inactive clause shows edit and delete but no deactivate", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={[CLONED_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    expect(screen.getByTitle("Edit clause")).toBeInTheDocument();
    expect(screen.getByTitle("Delete clause")).toBeInTheDocument();
    expect(screen.queryByTitle("Deactivate clause")).not.toBeInTheDocument();
  });

  it("hides action buttons when canManage is false", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={false}
      />,
    );

    expect(screen.queryByTitle("Clone clause")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Edit clause")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Delete clause")).not.toBeInTheDocument();
  });

  it("shows source badges correctly", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={false}
      />,
    );

    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByText("Custom")).toBeInTheDocument();
    expect(screen.getByText("Cloned")).toBeInTheDocument();
  });

  it("shows success message after clone confirmation", async () => {
    const user = userEvent.setup();
    mockCloneClause.mockResolvedValue({ success: true });

    render(
      <ClausesContent
        slug="acme"
        clauses={[SYSTEM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    await user.click(screen.getByTitle("Clone clause"));

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText("Clone this clause to create an editable copy?"),
      ).toBeInTheDocument();
    });

    // Confirm the clone
    await user.click(screen.getByRole("button", { name: "Clone" }));

    await waitFor(() => {
      expect(
        screen.getByText('Clause "Standard NDA" cloned successfully.'),
      ).toBeInTheDocument();
    });
  });

  it("shows error message when delete fails", async () => {
    const user = userEvent.setup();
    mockDeleteClause.mockResolvedValue({
      success: false,
      error: "This clause is referenced by templates and cannot be deleted.",
    });

    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    await user.click(screen.getByTitle("Delete clause"));

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText(
          "Are you sure you want to delete this clause? This action cannot be undone.",
        ),
      ).toBeInTheDocument();
    });

    // Confirm the delete
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(
        screen.getByText(
          "This clause is referenced by templates and cannot be deleted.",
        ),
      ).toBeInTheDocument();
    });
  });
});
