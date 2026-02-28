import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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

let mockClauseContent = {
  body: "<p>Payment is due within 30 days of invoice.</p>",
  title: "Payment Terms",
  isLoading: false,
};

vi.mock("@/components/editor/hooks/useClauseContent", () => ({
  useClauseContent: () => mockClauseContent,
}));

import { ClauseBlockNodeView } from "@/components/editor/node-views/ClauseBlockNodeView";

function createMockNodeViewProps(attrs: Record<string, unknown>) {
  return {
    node: {
      attrs,
      type: { name: "clauseBlock" },
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

describe("ClauseBlockNodeView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClauseContent = {
      body: "<p>Payment is due within 30 days of invoice.</p>",
      title: "Payment Terms",
      isLoading: false,
    };
  });

  afterEach(() => {
    cleanup();
  });

  it("renders clause title and required badge", () => {
    const props = createMockNodeViewProps({
      clauseId: "abc-123",
      slug: "payment-terms",
      title: "Payment Terms",
      required: true,
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<ClauseBlockNodeView {...(props as any)} />);

    expect(screen.getByText("Payment Terms")).toBeInTheDocument();
    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("shows loading state then content", () => {
    mockClauseContent = {
      body: null,
      title: null,
      isLoading: true,
    };

    const props = createMockNodeViewProps({
      clauseId: "abc-123",
      slug: "payment-terms",
      title: "Payment Terms",
      required: false,
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const { rerender } = render(<ClauseBlockNodeView {...(props as any)} />);

    expect(screen.getByText("Loading clause content...")).toBeInTheDocument();

    // Simulate loaded state
    mockClauseContent = {
      body: "<p>Payment is due within 30 days of invoice.</p>",
      title: "Payment Terms",
      isLoading: false,
    };

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rerender(<ClauseBlockNodeView {...(props as any)} />);

    expect(screen.getByText("Expand preview")).toBeInTheDocument();
  });

  it("toggles expand/collapse for content preview", async () => {
    const user = userEvent.setup();
    const props = createMockNodeViewProps({
      clauseId: "abc-123",
      slug: "payment-terms",
      title: "Payment Terms",
      required: false,
    });

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    render(<ClauseBlockNodeView {...(props as any)} />);

    // Initially collapsed
    expect(screen.getByText("Expand preview")).toBeInTheDocument();
    expect(
      screen.queryByText("Payment is due within 30 days of invoice."),
    ).not.toBeInTheDocument();

    // Click to expand
    await user.click(screen.getByText("Expand preview"));

    expect(screen.getByText("Collapse preview")).toBeInTheDocument();
    expect(
      screen.getByText("Payment is due within 30 days of invoice."),
    ).toBeInTheDocument();
  });
});
