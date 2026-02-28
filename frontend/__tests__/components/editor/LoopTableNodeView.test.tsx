import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("@tiptap/react", () => ({
  NodeViewWrapper: ({
    children,
    ...props
  }: React.PropsWithChildren<Record<string, unknown>>) => (
    <div data-testid="node-view-wrapper" {...props}>
      {children}
    </div>
  ),
}));

vi.mock("motion/react", () => ({
  motion: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    div: ({ children, ...props }: any) => {
      const { initial, animate, transition, ...rest } = props;
      void initial;
      void animate;
      void transition;
      return <div {...rest}>{children}</div>;
    },
  },
}));

import { LoopTableNodeView } from "@/components/editor/node-views/LoopTableNodeView";

function createMockNodeViewProps(attrs: Record<string, unknown>) {
  return {
    node: {
      attrs,
      type: { name: "loopTable" },
    },
    updateAttributes: vi.fn(),
    deleteNode: vi.fn(),
    selected: false,
    extension: {},
    getPos: vi.fn(() => 0),
    editor: {},
    decorations: [],
    innerDecorations: [],
  };
}

describe("LoopTableNodeView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table with column headers", () => {
    const props = createMockNodeViewProps({
      dataSource: "members",
      columns: [
        { header: "Name", key: "name" },
        { header: "Email", key: "email" },
      ],
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<LoopTableNodeView {...(props as any)} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Email")).toBeInTheDocument();
  });

  it("renders placeholder rows with key values", () => {
    const props = createMockNodeViewProps({
      dataSource: "members",
      columns: [
        { header: "Name", key: "name" },
        { header: "Email", key: "email" },
      ],
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<LoopTableNodeView {...(props as any)} />);

    const namePlaceholders = screen.getAllByText("{name}");
    const emailPlaceholders = screen.getAllByText("{email}");
    // 2 placeholder rows
    expect(namePlaceholders).toHaveLength(2);
    expect(emailPlaceholders).toHaveLength(2);
  });

  it("shows data source label", () => {
    const props = createMockNodeViewProps({
      dataSource: "members",
      columns: [{ header: "Name", key: "name" }],
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<LoopTableNodeView {...(props as any)} />);

    expect(screen.getByText("Data source: members")).toBeInTheDocument();
  });
});
