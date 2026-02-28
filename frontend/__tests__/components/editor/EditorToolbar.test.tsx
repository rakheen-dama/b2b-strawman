import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditorToolbar } from "@/components/editor/EditorToolbar";

function createMockEditor() {
  const mock: Record<string, unknown> = {};

  const chainable = {
    focus: vi.fn(() => chainable),
    toggleBold: vi.fn(() => chainable),
    toggleItalic: vi.fn(() => chainable),
    toggleUnderline: vi.fn(() => chainable),
    toggleHeading: vi.fn(() => chainable),
    toggleBulletList: vi.fn(() => chainable),
    toggleOrderedList: vi.fn(() => chainable),
    setHorizontalRule: vi.fn(() => chainable),
    insertTable: vi.fn(() => chainable),
    setLink: vi.fn(() => chainable),
    unsetLink: vi.fn(() => chainable),
    run: vi.fn(),
  };

  mock.chain = vi.fn(() => chainable);
  mock.isActive = vi.fn(() => false);
  mock.chainable = chainable;

  // Cast to Editor type for the component
  return mock as unknown as {
    chain: ReturnType<typeof vi.fn>;
    isActive: ReturnType<typeof vi.fn>;
    chainable: typeof chainable;
  };
}

describe("EditorToolbar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders toolbar buttons", () => {
    const editor = createMockEditor();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<EditorToolbar editor={editor as any} />);

    expect(screen.getByLabelText("Bold")).toBeInTheDocument();
    expect(screen.getByLabelText("Italic")).toBeInTheDocument();
    expect(screen.getByLabelText("Underline")).toBeInTheDocument();
    expect(screen.getByLabelText("Heading 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Heading 2")).toBeInTheDocument();
    expect(screen.getByLabelText("Heading 3")).toBeInTheDocument();
    expect(screen.getByLabelText("Bullet list")).toBeInTheDocument();
    expect(screen.getByLabelText("Ordered list")).toBeInTheDocument();
    expect(screen.getByLabelText("Insert table")).toBeInTheDocument();
    expect(screen.getByLabelText("Horizontal rule")).toBeInTheDocument();
    expect(screen.getByLabelText("Link")).toBeInTheDocument();
  });

  it("bold button toggles bold mark", async () => {
    const user = userEvent.setup();
    const editor = createMockEditor();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<EditorToolbar editor={editor as any} />);

    await user.click(screen.getByLabelText("Bold"));

    expect(editor.chain).toHaveBeenCalled();
    expect(editor.chainable.focus).toHaveBeenCalled();
    expect(editor.chainable.toggleBold).toHaveBeenCalled();
    expect(editor.chainable.run).toHaveBeenCalled();
  });
});
