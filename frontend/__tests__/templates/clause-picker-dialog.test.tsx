import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClausePickerDialog } from "@/components/templates/clause-picker-dialog";
import type { Clause } from "@/lib/actions/clause-actions";

const mockGetClauses = vi.fn();

vi.mock("@/lib/actions/clause-actions", () => ({
  getClauses: (...args: unknown[]) => mockGetClauses(...args),
}));

function makeBody(text: string): Record<string, unknown> {
  return {
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text }] }],
  };
}

const NDA_CLAUSE: Clause = {
  id: "c-1",
  title: "Standard NDA",
  slug: "standard-nda",
  description: "Non-disclosure agreement clause",
  body: makeBody("NDA body"),
  legacyBody: null,
  category: "Confidentiality",
  source: "SYSTEM",
  sourceClauseId: null,
  packId: "common-pack",
  active: true,
  sortOrder: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const LIABILITY_CLAUSE: Clause = {
  id: "c-2",
  title: "Liability Limitation",
  slug: "liability-limitation",
  description: "Limits liability exposure",
  body: makeBody("Liability body"),
  legacyBody: null,
  category: "Legal",
  source: "CUSTOM",
  sourceClauseId: null,
  packId: null,
  active: true,
  sortOrder: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const IP_CLAUSE: Clause = {
  id: "c-3",
  title: "IP Assignment",
  slug: "ip-assignment",
  description: "Intellectual property assignment",
  body: makeBody("IP body"),
  legacyBody: null,
  category: "Confidentiality",
  source: "SYSTEM",
  sourceClauseId: null,
  packId: "common-pack",
  active: true,
  sortOrder: 2,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const ALL_CLAUSES = [NDA_CLAUSE, LIABILITY_CLAUSE, IP_CLAUSE];

describe("ClausePickerDialog", () => {
  const mockOnConfirm = vi.fn();
  const mockOnOpenChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetClauses.mockResolvedValue(ALL_CLAUSES);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clauses grouped by category", async () => {
    render(
      <ClausePickerDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        existingClauseIds={[]}
        onConfirm={mockOnConfirm}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    expect(screen.getByText("Confidentiality")).toBeInTheDocument();
    expect(screen.getByText("Legal")).toBeInTheDocument();
    expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    expect(screen.getByText("IP Assignment")).toBeInTheDocument();
    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
  });

  it("shows already-associated clauses as disabled", async () => {
    render(
      <ClausePickerDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        existingClauseIds={["c-1"]}
        onConfirm={mockOnConfirm}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    expect(screen.getByText("Already added")).toBeInTheDocument();

    // The checkbox for NDA should be disabled
    const ndaCheckbox = screen.getByRole("checkbox", { name: "Select Standard NDA" });
    expect(ndaCheckbox).toBeDisabled();
  });

  it("filters clauses by search input", async () => {
    const user = userEvent.setup();
    render(
      <ClausePickerDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        existingClauseIds={[]}
        onConfirm={mockOnConfirm}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("Search clauses...");
    await user.type(searchInput, "Liability");

    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
    expect(screen.queryByText("Standard NDA")).not.toBeInTheDocument();
    expect(screen.queryByText("IP Assignment")).not.toBeInTheDocument();
  });

  it("calls onConfirm with selected clause IDs on add", async () => {
    const user = userEvent.setup();
    render(
      <ClausePickerDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        existingClauseIds={[]}
        onConfirm={mockOnConfirm}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    // Select NDA and Liability
    const ndaCheckbox = screen.getByRole("checkbox", { name: "Select Standard NDA" });
    const liabilityCheckbox = screen.getByRole("checkbox", { name: "Select Liability Limitation" });

    await user.click(ndaCheckbox);
    await user.click(liabilityCheckbox);

    const addBtn = screen.getByRole("button", { name: /Add Selected/i });
    expect(addBtn).toHaveTextContent("Add Selected (2)");

    await user.click(addBtn);

    expect(mockOnConfirm).toHaveBeenCalledWith([
      {
        id: "c-1",
        title: "Standard NDA",
        category: "Confidentiality",
        description: "Non-disclosure agreement clause",
        legacyBody: null,
      },
      {
        id: "c-2",
        title: "Liability Limitation",
        category: "Legal",
        description: "Limits liability exposure",
        legacyBody: null,
      },
    ]);
    expect(mockOnOpenChange).toHaveBeenCalledWith(false);
  });

  it("disables Add Selected button when nothing selected", async () => {
    render(
      <ClausePickerDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        existingClauseIds={[]}
        onConfirm={mockOnConfirm}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const addBtn = screen.getByRole("button", { name: /Add Selected/i });
    expect(addBtn).toBeDisabled();
  });
});
