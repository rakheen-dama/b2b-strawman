import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock Tiptap dependencies (must be before component import)
const mockSetContent = vi.fn();
const mockGetJSON = vi.fn(
  (): Record<string, unknown> => ({
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text: "test" }] }],
  }),
);
const mockSetEditable = vi.fn();
const mockOn = vi.fn();
const mockOff = vi.fn();
const mockDestroy = vi.fn();
const mockChain = vi.fn();
const mockIsActive = vi.fn(() => false);

let capturedEditable = true;

const mockEditor = {
  getJSON: mockGetJSON,
  commands: { setContent: mockSetContent },
  chain: mockChain,
  focus: vi.fn(() => mockEditor),
  run: vi.fn(),
  isActive: mockIsActive,
  isEditable: true,
  setEditable: mockSetEditable,
  on: mockOn,
  off: mockOff,
  destroy: mockDestroy,
};

vi.mock("@tiptap/react", () => ({
  useEditor: vi.fn((opts: Record<string, unknown>) => {
    capturedEditable = opts.editable as boolean;
    return mockEditor;
  }),
  EditorContent: vi.fn(({ editor }: { editor: unknown }) =>
    editor ? (
      <div data-testid="editor-content" contentEditable={capturedEditable} />
    ) : null,
  ),
}));

vi.mock("server-only", () => ({}));
vi.mock("@tiptap/starter-kit", () => ({ default: {} }));
vi.mock("@tiptap/extension-table", () => ({
  Table: { configure: () => ({}) },
  TableRow: {},
  TableCell: {},
  TableHeader: {},
}));
vi.mock("@tiptap/extension-link", () => ({
  default: { configure: () => ({}) },
}));
vi.mock("@tiptap/extension-underline", () => ({ default: {} }));
vi.mock("@tiptap/extension-placeholder", () => ({
  default: { configure: () => ({}) },
}));

vi.mock("@/lib/actions/clause-actions", async () => ({
  createClause: vi.fn(() => Promise.resolve({ success: true })),
  updateClause: vi.fn(() => Promise.resolve({ success: true })),
  cloneClause: vi.fn(() => Promise.resolve({ success: true })),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}));

import { ClauseEditorSheet } from "@/components/clauses/clause-editor-sheet";
import type { Clause } from "@/lib/actions/clause-actions";
import { createClause } from "@/lib/actions/clause-actions";

const baseClause: Clause = {
  id: "clause-1",
  title: "Test Clause",
  slug: "test-clause",
  description: "A test clause",
  body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "clause body" }] }] },
  legacyBody: null,
  category: "General",
  source: "CUSTOM",
  sourceClauseId: null,
  packId: null,
  active: true,
  sortOrder: 0,
  templateUsageCount: 2,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const systemClause: Clause = {
  ...baseClause,
  id: "clause-sys",
  title: "System Clause",
  slug: "system-clause",
  source: "SYSTEM",
};

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  slug: "test-org",
  clause: null as Clause | null,
  categories: ["General", "Legal", "Finance"],
  onSuccess: vi.fn(),
  onError: vi.fn(),
};

describe("ClauseEditorSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedEditable = true;
  });

  afterEach(() => cleanup());

  it("renders sheet with DocumentEditor", () => {
    render(<ClauseEditorSheet {...defaultProps} />);

    // "New Clause" appears in both sr-only SheetTitle and visible h2
    expect(screen.getAllByText("New Clause")).toHaveLength(2);
    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
    expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
    expect(screen.getByText("Create Clause")).toBeInTheDocument();
  });

  it("save sends JSON body from editor on create", async () => {
    const user = userEvent.setup();
    render(<ClauseEditorSheet {...defaultProps} />);

    // Fill in required fields
    await user.type(screen.getByLabelText(/title/i), "New Test Clause");

    // Select category via combobox button
    const categoryButton = screen.getByRole("combobox");
    await user.click(categoryButton);
    await user.click(screen.getByText("General"));

    // Click create
    await user.click(screen.getByText("Create Clause"));

    await waitFor(() => {
      expect(createClause).toHaveBeenCalledWith("test-org", {
        title: "New Test Clause",
        description: undefined,
        body: expect.objectContaining({ type: "doc" }),
        category: "General",
      });
    });
  });

  it("system clause is read-only with clone action", () => {
    render(<ClauseEditorSheet {...defaultProps} clause={systemClause} />);

    // "System Clause" appears in both sr-only SheetTitle and visible h2
    expect(screen.getAllByText("System Clause")).toHaveLength(2);
    expect(
      screen.getByText("This is a system clause. Clone to customize."),
    ).toBeInTheDocument();
    expect(screen.getByText("Clone")).toBeInTheDocument();
    // No Save/Create button for system clauses
    expect(screen.queryByText("Save Changes")).not.toBeInTheDocument();
    expect(screen.queryByText("Create Clause")).not.toBeInTheDocument();
  });

  it("edit mode shows warning about shared content", () => {
    render(<ClauseEditorSheet {...defaultProps} clause={baseClause} />);

    // "Edit Clause" appears in both sr-only SheetTitle and visible h2
    expect(screen.getAllByText("Edit Clause")).toHaveLength(2);
    expect(
      screen.getByText(
        "Editing this clause will affect all templates that use it.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Save Changes")).toBeInTheDocument();
  });
});
