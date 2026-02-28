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

// Mock Tiptap dependencies for DocumentEditor
const mockSetContent = vi.fn();
const mockGetJSON = vi.fn(
  (): Record<string, unknown> => ({ type: "doc", content: [] }),
);
const mockSetEditable = vi.fn();
const mockOn = vi.fn();
const mockOff = vi.fn();
const mockDestroy = vi.fn();
const mockChain = vi.fn();
const mockIsActive = vi.fn(() => false);

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
    return mockEditor;
  }),
  EditorContent: vi.fn(({ editor }: { editor: unknown }) =>
    editor ? <div data-testid="editor-content" /> : null,
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

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}));

function makeBody(text: string): Record<string, unknown> {
  return {
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: [{ type: "text", text }],
      },
    ],
  };
}

const SYSTEM_CLAUSE: Clause = {
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

const CUSTOM_CLAUSE: Clause = {
  id: "c-2",
  title: "Custom Liability",
  slug: "custom-liability",
  description: "Liability limitation clause",
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

const CLONED_CLAUSE: Clause = {
  id: "c-3",
  title: "Cloned NDA",
  slug: "cloned-nda",
  description: null,
  body: makeBody("Cloned NDA body"),
  legacyBody: null,
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

  it("hides action buttons when canManage is false", () => {
    render(
      <ClausesContent
        slug="acme"
        clauses={ALL_CLAUSES}
        categories={ALL_CATEGORIES}
        canManage={false}
      />,
    );

    // No dropdown menu triggers should be present
    expect(screen.queryByRole("button", { name: "" })).not.toBeInTheDocument();
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

  it("expands clause body on chevron click", async () => {
    const user = userEvent.setup();
    render(
      <ClausesContent
        slug="acme"
        clauses={[SYSTEM_CLAUSE]}
        categories={ALL_CATEGORIES}
        canManage={false}
      />,
    );

    // Editor content should NOT be visible initially
    expect(screen.queryByTestId("editor-content")).not.toBeInTheDocument();

    // Click the expand button on the clause card
    const expandButton = screen.getByLabelText("Expand clause");
    await user.click(expandButton);

    // Editor content should now be visible
    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
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

    // Open the dropdown menu
    const menuButtons = screen.getAllByRole("button");
    const moreButton = menuButtons.find(
      (btn) => btn.querySelector("svg") && btn.className.includes("size-8"),
    );
    if (moreButton) await user.click(moreButton);

    // Click "Clone & Customize"
    await waitFor(() => {
      expect(screen.getByText("Clone & Customize")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Clone & Customize"));

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
});
