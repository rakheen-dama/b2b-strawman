import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockClauses = [
  {
    id: "clause-1",
    title: "Payment Terms",
    slug: "payment-terms",
    description: "Standard payment terms",
    body: "<p>Payment is due within 30 days.</p>",
    category: "Financial",
    source: "SYSTEM" as const,
    sourceClauseId: null,
    packId: null,
    active: true,
    sortOrder: 1,
    createdAt: "2026-01-01",
    updatedAt: "2026-01-01",
  },
  {
    id: "clause-2",
    title: "Confidentiality",
    slug: "confidentiality",
    description: "NDA clause",
    body: "<p>All information shall be kept confidential.</p>",
    category: "Legal",
    source: "SYSTEM" as const,
    sourceClauseId: null,
    packId: null,
    active: true,
    sortOrder: 2,
    createdAt: "2026-01-01",
    updatedAt: "2026-01-01",
  },
  {
    id: "clause-3",
    title: "Late Payment Penalty",
    slug: "late-payment-penalty",
    description: null,
    body: "<p>Late payments incur a 2% penalty.</p>",
    category: "Financial",
    source: "CUSTOM" as const,
    sourceClauseId: null,
    packId: null,
    active: true,
    sortOrder: 3,
    createdAt: "2026-01-01",
    updatedAt: "2026-01-01",
  },
];

vi.mock("@/lib/actions/clause-actions", () => ({
  getClauses: vi.fn(() => Promise.resolve(mockClauses)),
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

import { ClausePicker } from "@/components/editor/ClausePicker";

describe("ClausePicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders grouped clauses when open", async () => {
    render(
      <ClausePicker
        onSelect={vi.fn()}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Insert Clause")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("Financial")).toBeInTheDocument();
      expect(screen.getByText("Legal")).toBeInTheDocument();
      expect(screen.getByText("Payment Terms")).toBeInTheDocument();
      expect(screen.getByText("Confidentiality")).toBeInTheDocument();
    });
  });

  it("filters clauses with search input", async () => {
    const user = userEvent.setup();

    render(
      <ClausePicker
        onSelect={vi.fn()}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Payment Terms")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("Search clauses...");
    await user.type(searchInput, "confidential");

    await waitFor(() => {
      expect(screen.getByText("Confidentiality")).toBeInTheDocument();
      expect(screen.queryByText("Payment Terms")).not.toBeInTheDocument();
    });
  });

  it("calls onSelect when insert button is clicked", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(
      <ClausePicker
        onSelect={onSelect}
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Payment Terms")).toBeInTheDocument();
    });

    // Click to select a clause
    await user.click(screen.getByText("Payment Terms"));

    // Preview should show
    await waitFor(() => {
      expect(screen.getByText("Standard payment terms")).toBeInTheDocument();
    });

    // Click insert button
    await user.click(screen.getByRole("button", { name: "Insert" }));

    expect(onSelect).toHaveBeenCalledWith({
      id: "clause-1",
      slug: "payment-terms",
      title: "Payment Terms",
      required: false,
    });
  });
});
