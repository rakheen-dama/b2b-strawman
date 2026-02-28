import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

const mockSetContent = vi.fn();
const mockGetJSON = vi.fn((): Record<string, unknown> => ({ type: "doc", content: [] }));
const mockSetEditable = vi.fn();
const mockOn = vi.fn();
const mockOff = vi.fn();
const mockDestroy = vi.fn();
const mockChain = vi.fn();
const mockIsActive = vi.fn(() => false);

let capturedOnUpdate: ((params: { editor: unknown }) => void) | undefined;
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
    capturedOnUpdate = opts.onUpdate as typeof capturedOnUpdate;
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
vi.mock("@/lib/actions/clause-actions", () => ({
  getClause: vi.fn(() => Promise.resolve(null)),
  getClauses: vi.fn(() => Promise.resolve([])),
}));

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

import { DocumentEditor } from "@/components/editor/DocumentEditor";

describe("DocumentEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedEditable = true;
  });

  afterEach(() => {
    cleanup();
  });

  it("renders editor content area", () => {
    render(<DocumentEditor />);
    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
  });

  it("loads Tiptap JSON content", () => {
    const content = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello World" }],
        },
      ],
    };

    render(<DocumentEditor content={content} />);
    expect(screen.getByTestId("editor-content")).toBeInTheDocument();
  });

  it("calls onUpdate when content changes", () => {
    const handleUpdate = vi.fn();
    render(<DocumentEditor onUpdate={handleUpdate} />);

    // Simulate Tiptap calling the onUpdate callback
    const fakeJSON = { type: "doc", content: [{ type: "paragraph" }] };
    mockGetJSON.mockReturnValue(fakeJSON);

    if (capturedOnUpdate) {
      capturedOnUpdate({ editor: mockEditor });
    }

    expect(handleUpdate).toHaveBeenCalledWith(fakeJSON);
  });

  it("respects editable=false", () => {
    render(<DocumentEditor editable={false} />);
    const editorContent = screen.getByTestId("editor-content");
    expect(editorContent).toHaveAttribute("contenteditable", "false");
  });
});
