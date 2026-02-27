import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import { GenerationClauseStep } from "@/components/templates/generation-clause-step";
import type { TemplateClauseDetail } from "@/lib/actions/template-clause-actions";

// Mock server-only (imported transitively via template-clause-actions -> api)
vi.mock("server-only", () => ({}));

const mockGetTemplateClauses = vi.fn();
const mockSetTemplateClauses = vi.fn();
const mockPreviewTemplate = vi.fn();
const mockGenerateDocument = vi.fn();
const mockGetClauses = vi.fn();

vi.mock("@/lib/actions/template-clause-actions", () => ({
  getTemplateClauses: (...args: unknown[]) => mockGetTemplateClauses(...args),
  setTemplateClauses: (...args: unknown[]) => mockSetTemplateClauses(...args),
}));

vi.mock("@/lib/actions/clause-actions", () => ({
  getClauses: (...args: unknown[]) => mockGetClauses(...args),
}));

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  previewTemplateAction: (...args: unknown[]) => mockPreviewTemplate(...args),
  generateDocumentAction: (...args: unknown[]) => mockGenerateDocument(...args),
}));

const REQUIRED_CLAUSE: TemplateClauseDetail = {
  id: "tc-1",
  clauseId: "c-1",
  title: "Standard NDA",
  slug: "standard-nda",
  category: "Confidentiality",
  description: "Non-disclosure agreement",
  bodyPreview: "<p>NDA body</p>",
  required: true,
  sortOrder: 0,
  active: true,
};

const OPTIONAL_CLAUSE: TemplateClauseDetail = {
  id: "tc-2",
  clauseId: "c-2",
  title: "Liability Limitation",
  slug: "liability-limitation",
  category: "Legal",
  description: "Limits liability",
  bodyPreview: "<p>Liability body</p>",
  required: false,
  sortOrder: 1,
  active: true,
};

const TEMPLATE_CLAUSES = [REQUIRED_CLAUSE, OPTIONAL_CLAUSE];

describe("GenerationClauseStep", () => {
  const mockOnNext = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);
    mockGetClauses.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clause list with titles and badges", async () => {
    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });
    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
    expect(screen.getByText("Confidentiality")).toBeInTheDocument();
    expect(screen.getByText("Legal")).toBeInTheDocument();
    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("required clauses cannot be unchecked", async () => {
    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    const requiredCheckbox = screen.getByRole("checkbox", { name: "Select Standard NDA" });
    expect(requiredCheckbox).toBeDisabled();
    expect(requiredCheckbox).toHaveAttribute("data-state", "checked");
  });

  it("optional clauses are checked by default and toggleable", async () => {
    const user = userEvent.setup();
    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
    });

    const optionalCheckbox = screen.getByRole("checkbox", { name: "Select Liability Limitation" });
    expect(optionalCheckbox).not.toBeDisabled();
    expect(optionalCheckbox).toHaveAttribute("data-state", "checked");

    // Toggle off
    await user.click(optionalCheckbox);
    expect(optionalCheckbox).toHaveAttribute("data-state", "unchecked");

    // Toggle back on
    await user.click(optionalCheckbox);
    expect(optionalCheckbox).toHaveAttribute("data-state", "checked");
  });

  it("browse library opens clause picker dialog", async () => {
    const user = userEvent.setup();
    mockGetClauses.mockResolvedValue([
      {
        id: "c-3",
        title: "IP Assignment",
        slug: "ip-assignment",
        description: "IP clause",
        body: "<p>IP</p>",
        category: "IP",
        source: "SYSTEM",
        sourceClauseId: null,
        packId: null,
        active: true,
        sortOrder: 0,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);

    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Browse Library/i }));

    await waitFor(() => {
      expect(screen.getByText("Add Clauses")).toBeInTheDocument();
    });

    // Select the IP clause
    await waitFor(() => {
      expect(screen.getByText("IP Assignment")).toBeInTheDocument();
    });

    const ipCheckbox = screen.getByRole("checkbox", { name: "Select IP Assignment" });
    await user.click(ipCheckbox);

    await user.click(screen.getByRole("button", { name: /Add Selected/i }));

    // After picker closes, the clause should be in the step list
    await waitFor(() => {
      expect(screen.queryByText("Add Clauses")).not.toBeInTheDocument();
    });
    expect(screen.getByText("IP Assignment")).toBeInTheDocument();
    expect(screen.getByText("IP")).toBeInTheDocument();
  });

  it("next button passes selected clauses to parent", async () => {
    const user = userEvent.setup();
    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Next: Preview/i }));

    expect(mockOnNext).toHaveBeenCalledWith([
      expect.objectContaining({ clauseId: "c-1", required: true, sortOrder: 0 }),
      expect.objectContaining({ clauseId: "c-2", required: false, sortOrder: 1 }),
    ]);
  });

  it("unchecked optional clauses are excluded from next callback", async () => {
    const user = userEvent.setup();
    render(
      <GenerationClauseStep templateId="tpl-1" onNext={mockOnNext} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
    });

    // Uncheck the optional clause
    const optionalCheckbox = screen.getByRole("checkbox", { name: "Select Liability Limitation" });
    await user.click(optionalCheckbox);

    await user.click(screen.getByRole("button", { name: /Next: Preview/i }));

    expect(mockOnNext).toHaveBeenCalledWith([
      expect.objectContaining({ clauseId: "c-1", required: true, sortOrder: 0 }),
    ]);
  });
});

describe("GenerateDocumentDialog with clauses", () => {
  const baseProps = {
    templateId: "tpl-1",
    templateName: "Engagement Letter",
    entityId: "proj-1",
    entityType: "PROJECT",
    open: true,
    onOpenChange: vi.fn(),
    onSaved: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetClauses.mockResolvedValue([]);
    mockPreviewTemplate.mockResolvedValue({
      success: true,
      html: "<h1>Preview Content</h1>",
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("shows clause step when template has clause associations", async () => {
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);

    render(<GenerateDocumentDialog {...baseProps} />);

    await waitFor(() => {
      expect(screen.getByText("Select Clauses")).toBeInTheDocument();
    });

    expect(screen.getByText("Step 1 of 2")).toBeInTheDocument();
  });

  it("skips clause step when template has no clause associations", async () => {
    mockGetTemplateClauses.mockResolvedValue([]);

    render(<GenerateDocumentDialog {...baseProps} />);

    // Should go directly to preview -- no clause step
    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    expect(screen.queryByText("Select Clauses")).not.toBeInTheDocument();
    expect(screen.queryByText("Step 1 of 2")).not.toBeInTheDocument();
  });

  it("back button preserves clause selections", async () => {
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Wait for clause step
    await waitFor(() => {
      expect(screen.getByText("Select Clauses")).toBeInTheDocument();
    });

    // Click Next to go to preview
    await user.click(screen.getByRole("button", { name: /Next: Preview/i }));

    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    // Click Back to return to clause step
    await user.click(screen.getByRole("button", { name: /Back/i }));

    await waitFor(() => {
      expect(screen.getByText("Select Clauses")).toBeInTheDocument();
    });

    // Clauses should still be shown
    expect(screen.getByText("Standard NDA")).toBeInTheDocument();
    expect(screen.getByText("Liability Limitation")).toBeInTheDocument();
  });

  it("preview includes selected clauses", async () => {
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Wait for clause step
    await waitFor(() => {
      expect(screen.getByText("Select Clauses")).toBeInTheDocument();
    });

    // Click Next to go to preview
    await user.click(screen.getByRole("button", { name: /Next: Preview/i }));

    await waitFor(() => {
      expect(mockPreviewTemplate).toHaveBeenCalledWith(
        "tpl-1",
        "proj-1",
        expect.arrayContaining([
          expect.objectContaining({ clauseId: "c-1", sortOrder: 0 }),
          expect.objectContaining({ clauseId: "c-2", sortOrder: 1 }),
        ]),
      );
    });
  });

  it("generate includes selected clauses", async () => {
    mockGetTemplateClauses.mockResolvedValue(TEMPLATE_CLAUSES);
    mockGenerateDocument.mockResolvedValue({
      success: true,
      data: { id: "gen-1", fileName: "test.pdf", fileSize: 1024, documentId: "doc-1", generatedAt: "2026-01-01T00:00:00Z" },
    });

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Wait for clause step
    await waitFor(() => {
      expect(screen.getByText("Select Clauses")).toBeInTheDocument();
    });

    // Click Next to go to preview
    await user.click(screen.getByRole("button", { name: /Next: Preview/i }));

    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    // Click Save
    await user.click(screen.getByRole("button", { name: /Save to Documents/i }));

    await waitFor(() => {
      expect(mockGenerateDocument).toHaveBeenCalledWith(
        "tpl-1",
        "proj-1",
        true,
        false,
        expect.arrayContaining([
          expect.objectContaining({ clauseId: "c-1", sortOrder: 0 }),
          expect.objectContaining({ clauseId: "c-2", sortOrder: 1 }),
        ]),
      );
    });
  });

  it("backward compatible -- no clause step, preview and generate work without clauses", async () => {
    mockGetTemplateClauses.mockResolvedValue([]);
    mockGenerateDocument.mockResolvedValue({
      success: true,
      pdfBase64: btoa("fake-pdf"),
    });

    const user = userEvent.setup();
    render(<GenerateDocumentDialog {...baseProps} />);

    // Should go directly to preview
    await waitFor(() => {
      expect(screen.getByTitle("Document Preview")).toBeInTheDocument();
    });

    expect(mockPreviewTemplate).toHaveBeenCalledWith("tpl-1", "proj-1", undefined);

    await user.click(screen.getByRole("button", { name: /Download PDF/i }));

    await waitFor(() => {
      expect(mockGenerateDocument).toHaveBeenCalledWith(
        "tpl-1",
        "proj-1",
        false,
        false,
        undefined,
      );
    });
  });
});
