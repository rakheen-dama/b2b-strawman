import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FolderOpen, Users } from "lucide-react";

// Mock next/link as a simple anchor
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

afterEach(() => {
  cleanup();
  delete process.env.NEXT_PUBLIC_DOCS_URL;
});

// ─── docsLink utility ──────────────────────────────────────────────────────────

describe("docsLink", () => {
  it("returns default base URL + path", async () => {
    vi.resetModules();
    delete process.env.NEXT_PUBLIC_DOCS_URL;
    const { docsLink } = await import("@/lib/docs");

    expect(docsLink("/features/invoicing")).toBe("https://docs.heykazi.com/features/invoicing");
  });

  it("uses NEXT_PUBLIC_DOCS_URL override when set", async () => {
    vi.resetModules();
    process.env.NEXT_PUBLIC_DOCS_URL = "http://localhost:3003";
    const { docsLink } = await import("@/lib/docs");

    expect(docsLink("/features/tasks")).toBe("http://localhost:3003/features/tasks");
  });
});

// ─── HelpTip docsPath prop ────────────────────────────────────────────────────

describe("HelpTip docsPath", () => {
  it("renders Learn more link when docsPath is provided", async () => {
    vi.resetModules();
    delete process.env.NEXT_PUBLIC_DOCS_URL;

    const user = userEvent.setup();
    const { HelpTip } = await import("@/components/help-tip");

    render(<HelpTip code="rates.hierarchy" docsPath="/features/rate-cards-budgets" />);

    // Open popover
    await user.click(screen.getByRole("button", { name: /help/i }));

    const link = screen.getByRole("link", { name: /learn more/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(link).toHaveAttribute("href", "https://docs.heykazi.com/features/rate-cards-budgets");
  });

  it("does NOT render Learn more link when docsPath is omitted", async () => {
    vi.resetModules();
    delete process.env.NEXT_PUBLIC_DOCS_URL;

    const user = userEvent.setup();
    const { HelpTip } = await import("@/components/help-tip");

    render(<HelpTip code="rates.hierarchy" />);

    await user.click(screen.getByRole("button", { name: /help/i }));

    expect(screen.queryByRole("link", { name: /learn more/i })).not.toBeInTheDocument();
  });
});

// ─── EmptyState external URL detection ───────────────────────────────────────

describe("EmptyState secondaryLink external URL", () => {
  it("renders <a target=_blank> for external href (starts with http)", async () => {
    const { EmptyState } = await import("@/components/empty-state");

    render(
      <EmptyState
        icon={FolderOpen}
        title="No projects"
        description="Get started."
        secondaryLink={{
          label: "Read the guide",
          href: "https://docs.heykazi.com/features/projects",
        }}
      />
    );

    const link = screen.getByText("Read the guide");
    expect(link.closest("a")).toHaveAttribute("href", "https://docs.heykazi.com/features/projects");
    expect(link.closest("a")).toHaveAttribute("target", "_blank");
    expect(link.closest("a")).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("renders <Link> (internal nav) for relative href (no target=_blank)", async () => {
    const { EmptyState } = await import("@/components/empty-state");

    render(
      <EmptyState
        icon={Users}
        title="No customers"
        description="Add one."
        secondaryLink={{ label: "Learn more", href: "/help" }}
      />
    );

    const link = screen.getByText("Learn more");
    expect(link.closest("a")).toHaveAttribute("href", "/help");
    expect(link.closest("a")).not.toHaveAttribute("target");
  });
});

// ─── UTILITY_ITEMS Help item ──────────────────────────────────────────────────

describe("UTILITY_ITEMS Help item", () => {
  it("contains Help item with external: true", async () => {
    const { UTILITY_ITEMS } = await import("@/lib/nav-items");

    const helpItem = UTILITY_ITEMS.find((i) => i.label === "Help");
    expect(helpItem).toBeDefined();
    expect(helpItem?.external).toBe(true);
    expect(helpItem?.href("any-slug")).toBe("https://docs.heykazi.com");
  });
});
