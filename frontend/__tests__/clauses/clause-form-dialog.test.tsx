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

const ALL_CATEGORIES = ["Confidentiality", "Legal"];

describe("ClauseFormDialog and Confirmations", () => {
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

  it("renders_create_form_empty", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Click the "New Clause" button to open dialog
    await user.click(screen.getByText("New Clause"));

    // Dialog should be open with empty fields
    await waitFor(() => {
      expect(screen.getByText("New Clause", { selector: "[data-slot='dialog-title']" })).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/Title/)).toHaveValue("");
    expect(screen.getByLabelText(/Description/)).toHaveValue("");
  });

  it("renders_edit_form_pre_populated", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Click the Edit button
    await user.click(screen.getByTitle("Edit clause"));

    // Dialog should open with pre-populated fields
    await waitFor(() => {
      expect(screen.getByText("Edit Clause")).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/Title/)).toHaveValue("Custom Liability");
    expect(screen.getByLabelText(/Description/)).toHaveValue(
      "Liability limitation clause",
    );
  });

  it("validates_required_fields", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    await user.click(screen.getByText("New Clause"));

    await waitFor(() => {
      expect(screen.getByText("New Clause", { selector: "[data-slot='dialog-title']" })).toBeInTheDocument();
    });

    // The Create Clause button should be disabled when fields are empty
    const submitButton = screen.getByRole("button", { name: "Create Clause" });
    expect(submitButton).toBeDisabled();
  });

  it("category_combobox_shows_existing_categories", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    await user.click(screen.getByText("New Clause"));

    await waitFor(() => {
      expect(screen.getByText("New Clause", { selector: "[data-slot='dialog-title']" })).toBeInTheDocument();
    });

    // Click category combobox trigger
    await user.click(screen.getByRole("combobox"));

    // Categories should appear in the dropdown
    await waitFor(() => {
      expect(screen.getByText("Confidentiality")).toBeInTheDocument();
      // "Legal" already visible as category heading, so check for it within the combobox context
    });
  });

  it("preview_button_shows_body_html", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Open edit dialog (which has body pre-populated)
    await user.click(screen.getByTitle("Edit clause"));

    await waitFor(() => {
      expect(screen.getByText("Edit Clause")).toBeInTheDocument();
    });

    // Click Preview button
    const previewButton = screen.getByText("Preview");
    await user.click(previewButton);

    // Should show the preview panel with an iframe
    await waitFor(() => {
      expect(screen.getByTitle("Clause Preview")).toBeInTheDocument();
    });
  });

  it("clone_action_shows_confirmation_and_creates_copy", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[SYSTEM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Click clone button
    await user.click(screen.getByTitle("Clone clause"));

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText("Clone this clause to create an editable copy?"),
      ).toBeInTheDocument();
    });

    // Click Clone confirm button
    await user.click(screen.getByRole("button", { name: "Clone" }));

    await waitFor(() => {
      expect(mockCloneClause).toHaveBeenCalledWith("acme", "c-1");
    });

    // Success message should appear
    await waitFor(() => {
      expect(
        screen.getByText('Clause "Standard NDA" cloned successfully.'),
      ).toBeInTheDocument();
    });
  });

  it("deactivate_action_shows_confirmation_and_deactivates", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Click deactivate button
    await user.click(screen.getByTitle("Deactivate clause"));

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText(
          "This clause will be hidden from the clause picker but preserved on existing templates.",
        ),
      ).toBeInTheDocument();
    });

    // Click Deactivate confirm button
    await user.click(screen.getByRole("button", { name: "Deactivate" }));

    await waitFor(() => {
      expect(mockDeactivateClause).toHaveBeenCalledWith("acme", "c-2");
    });
  });

  it("submit_creates_clause_with_correct_payload", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[CUSTOM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={true}
      />,
    );

    // Open the New Clause dialog
    await user.click(screen.getByText("New Clause"));

    await waitFor(() => {
      expect(screen.getByText("New Clause", { selector: "[data-slot='dialog-title']" })).toBeInTheDocument();
    });

    // Fill in title
    await user.type(screen.getByLabelText(/Title/), "Payment Terms");

    // Select a category from the combobox
    await user.click(screen.getByRole("combobox"));
    await waitFor(() => {
      expect(screen.getByRole("option", { name: /Confidentiality/ })).toBeInTheDocument();
    });
    await user.click(screen.getByRole("option", { name: /Legal/ }));

    // Fill in body
    await user.type(screen.getByLabelText(/Body/), "<p>Net 30 days</p>");

    // Submit
    const submitButton = screen.getByRole("button", { name: "Create Clause" });
    expect(submitButton).not.toBeDisabled();
    await user.click(submitButton);

    await waitFor(() => {
      expect(mockCreateClause).toHaveBeenCalledWith("acme", {
        title: "Payment Terms",
        description: undefined,
        body: "<p>Net 30 days</p>",
        category: "Legal",
      });
    });
  });

  it("delete_409_shows_error", async () => {
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

    // Click delete button
    await user.click(screen.getByTitle("Delete clause"));

    // Confirmation dialog should appear
    await waitFor(() => {
      expect(
        screen.getByText(
          "Are you sure you want to delete this clause? This action cannot be undone.",
        ),
      ).toBeInTheDocument();
    });

    // Click Delete confirm button
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(mockDeleteClause).toHaveBeenCalledWith("acme", "c-2");
    });

    // Error message should appear
    await waitFor(() => {
      expect(
        screen.getByText(
          "This clause is referenced by templates and cannot be deleted.",
        ),
      ).toBeInTheDocument();
    });
  });
});
