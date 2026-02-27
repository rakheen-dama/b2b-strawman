import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplateClausesTab } from "@/components/templates/template-clauses-tab";
import type { TemplateClauseDetail } from "@/lib/actions/template-clause-actions";

const mockGetTemplateClauses = vi.fn();
const mockSetTemplateClauses = vi.fn();

vi.mock("@/lib/actions/template-clause-actions", () => ({
  getTemplateClauses: (...args: unknown[]) => mockGetTemplateClauses(...args),
  setTemplateClauses: (...args: unknown[]) => mockSetTemplateClauses(...args),
  addClauseToTemplate: vi.fn(),
  removeClauseFromTemplate: vi.fn(),
}));

const mockGetClauses = vi.fn();

vi.mock("@/lib/actions/clause-actions", () => ({
  getClauses: (...args: unknown[]) => mockGetClauses(...args),
}));

const CLAUSE_A: TemplateClauseDetail = {
  id: "tc-1",
  clauseId: "c-1",
  title: "Standard NDA",
  slug: "standard-nda",
  category: "Confidentiality",
  description: "Non-disclosure agreement",
  bodyPreview: "<p>NDA body</p>",
  required: false,
  sortOrder: 0,
  active: true,
};

const CLAUSE_B: TemplateClauseDetail = {
  id: "tc-2",
  clauseId: "c-2",
  title: "Liability Limitation",
  slug: "liability-limitation",
  category: "Legal",
  description: "Limits liability",
  bodyPreview: "<p>Liability body</p>",
  required: true,
  sortOrder: 1,
  active: true,
};

const TEMPLATE_CLAUSES = [CLAUSE_A, CLAUSE_B];

describe("TemplateClausesTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);
    mockSetTemplateClauses.mockResolvedValue({ success: true });
    mockGetClauses.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clause list with titles", async () => {
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });
    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
  });

  it("toggles required state when switch is clicked", async () => {
    const user = userEvent.setup();
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const toggles = screen.getAllByRole("switch");
    // CLAUSE_A starts as not required (unchecked)
    expect(toggles[0]).toHaveAttribute("data-state", "unchecked");
    // CLAUSE_B starts as required (checked)
    expect(toggles[1]).toHaveAttribute("data-state", "checked");

    await user.click(toggles[0]);
    expect(toggles[0]).toHaveAttribute("data-state", "checked");
  });

  it("removes clause when X button is clicked", async () => {
    const user = userEvent.setup();
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const removeBtn = screen.getByRole("button", { name: "Remove Standard NDA" });
    await user.click(removeBtn);

    expect(screen.queryByText("Standard NDA")).not.toBeInTheDocument();
    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
  });

  it("opens clause picker when Add Clause is clicked", async () => {
    const user = userEvent.setup();
    mockGetClauses.mockResolvedValue([]);

    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const addBtn = screen.getByRole("button", { name: /Add Clause/i });
    await user.click(addBtn);

    await waitFor(() => {
      expect(screen.getByText("Add Clauses")).toBeInTheDocument();
    });
  });

  it("calls setTemplateClauses with correct data on save", async () => {
    const user = userEvent.setup();
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    // Toggle required on first clause to create unsaved changes
    const toggles = screen.getAllByRole("switch");
    await user.click(toggles[0]);

    const saveBtn = screen.getByRole("button", { name: /Save/i });
    await user.click(saveBtn);

    await waitFor(() => {
      expect(mockSetTemplateClauses).toHaveBeenCalledWith(
        "t-1",
        [
          { clauseId: "c-1", sortOrder: 0, required: true },
          { clauseId: "c-2", sortOrder: 1, required: true },
        ],
        "acme",
      );
    });
  });

  it("shows empty state when no clauses", async () => {
    mockGetTemplateClauses.mockResolvedValue([]);

    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(
        screen.getByText(/No clauses configured/),
      ).toBeInTheDocument();
    });
  });

  it("shows unsaved changes indicator after modification", async () => {
    const user = userEvent.setup();
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    expect(screen.queryByText("Unsaved changes")).not.toBeInTheDocument();

    // Toggle required to make a change
    const toggles = screen.getAllByRole("switch");
    await user.click(toggles[0]);

    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
  });

  it("hides action buttons in read-only mode", async () => {
    render(
      <TemplateClausesTab templateId="t-1" slug="acme" readOnly />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    expect(screen.queryByRole("button", { name: /Add Clause/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Save/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Remove/i })).not.toBeInTheDocument();
  });
});
