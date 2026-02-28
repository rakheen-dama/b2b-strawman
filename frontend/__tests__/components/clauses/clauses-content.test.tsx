import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock Tiptap dependencies (must be before component import)
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

vi.mock("@/lib/actions/clause-actions", () => ({
  cloneClause: vi.fn(() => Promise.resolve({ success: true })),
  deactivateClause: vi.fn(() => Promise.resolve({ success: true })),
  deleteClause: vi.fn(() => Promise.resolve({ success: true })),
  getClauses: vi.fn(() => Promise.resolve([])),
  getClauseCategories: vi.fn(() => Promise.resolve([])),
  createClause: vi.fn(() => Promise.resolve({ success: true })),
  updateClause: vi.fn(() => Promise.resolve({ success: true })),
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

import { ClausesContent } from "@/components/clauses/clauses-content";
import type { Clause } from "@/lib/actions/clause-actions";

function makeClause(overrides: Partial<Clause> = {}): Clause {
  return {
    id: "clause-1",
    title: "Standard NDA",
    slug: "standard-nda",
    description: "A standard non-disclosure agreement clause",
    body: {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "NDA content here" }],
        },
      ],
    },
    legacyBody: null,
    category: "Legal",
    source: "SYSTEM",
    sourceClauseId: null,
    packId: null,
    active: true,
    sortOrder: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("ClausesContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedEditable = true;
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clauses grouped by category", () => {
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "NDA Clause", category: "Legal", sortOrder: 1 }),
      makeClause({ id: "2", title: "Payment Terms", category: "Finance", sortOrder: 1 }),
      makeClause({ id: "3", title: "Liability Clause", category: "Legal", sortOrder: 2 }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["Legal", "Finance"]}
        canManage={true}
      />,
    );

    // Both category headers should be visible
    expect(screen.getByText("Legal")).toBeInTheDocument();
    expect(screen.getByText("Finance")).toBeInTheDocument();

    // All clause titles should be visible (categories are expanded by default)
    expect(screen.getByText("NDA Clause")).toBeInTheDocument();
    expect(screen.getByText("Payment Terms")).toBeInTheDocument();
    expect(screen.getByText("Liability Clause")).toBeInTheDocument();
  });

  it("expands clause body on chevron click", async () => {
    const user = userEvent.setup();
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "Test Clause", category: "General" }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["General"]}
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

  it("shows source badges correctly", () => {
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "System Clause", source: "SYSTEM", category: "General" }),
      makeClause({ id: "2", title: "Cloned Clause", source: "CLONED", category: "General" }),
      makeClause({ id: "3", title: "Custom Clause", source: "CUSTOM", category: "General" }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["General"]}
        canManage={false}
      />,
    );

    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByText("Cloned")).toBeInTheDocument();
    expect(screen.getByText("Custom")).toBeInTheDocument();
  });

  it("hides actions menu when canManage is false", () => {
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "Test Clause", category: "General" }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["General"]}
        canManage={false}
      />,
    );

    expect(screen.queryByLabelText("Clause actions")).not.toBeInTheDocument();
  });

  it("shows 'Clone & Customize' but not 'Edit' or 'Deactivate' for system clauses", async () => {
    const user = userEvent.setup();
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "System Clause", source: "SYSTEM", category: "General", active: true }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["General"]}
        canManage={true}
      />,
    );

    const trigger = screen.getByLabelText("Clause actions");
    await user.click(trigger);

    expect(screen.getByText("Clone & Customize")).toBeInTheDocument();
    expect(screen.queryByText("Edit")).not.toBeInTheDocument();
    expect(screen.queryByText("Deactivate")).not.toBeInTheDocument();
  });

  it("shows 'Edit', 'Clone', and 'Deactivate' for active custom clauses", async () => {
    const user = userEvent.setup();
    const clauses: Clause[] = [
      makeClause({ id: "1", title: "Custom Clause", source: "CUSTOM", category: "General", active: true }),
    ];

    render(
      <ClausesContent
        slug="test-org"
        clauses={clauses}
        categories={["General"]}
        canManage={true}
      />,
    );

    const trigger = screen.getByLabelText("Clause actions");
    await user.click(trigger);

    expect(screen.getByText("Edit")).toBeInTheDocument();
    expect(screen.getByText("Clone")).toBeInTheDocument();
    expect(screen.getByText("Deactivate")).toBeInTheDocument();
  });

  it("shows 'Migration needed' badge for legacy content", () => {
    const legacyClause = makeClause({
      id: "legacy-1",
      title: "Legacy Clause",
      category: "General",
      body: {
        type: "doc",
        content: [
          {
            type: "legacyHtml",
            attrs: {
              html: "<p>Old HTML content</p>",
              complexity: "complex",
            },
          },
        ],
      },
    });

    const normalClause = makeClause({
      id: "normal-1",
      title: "Normal Clause",
      category: "General",
    });

    render(
      <ClausesContent
        slug="test-org"
        clauses={[legacyClause, normalClause]}
        categories={["General"]}
        canManage={false}
      />,
    );

    // The "Migration needed" badge should appear once (for the legacy clause)
    const badges = screen.getAllByText("Migration needed");
    expect(badges).toHaveLength(1);
  });
});
