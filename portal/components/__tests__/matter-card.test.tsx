import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/matters",
}));

// Mock next/link
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

import { MatterCard } from "@/components/matter-card";

describe("MatterCard", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders project name, description, and document count", () => {
    render(
      <MatterCard
        matter={{
          id: "proj-1",
          name: "Website Redesign",
          description: "Redesigning the company website for modern standards",
          documentCount: 5,
          createdAt: new Date().toISOString(),
          status: "ACTIVE",
        }}
      />,
    );

    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(
      screen.getByText("Redesigning the company website for modern standards"),
    ).toBeInTheDocument();
    expect(screen.getByText("5 documents")).toBeInTheDocument();
  });

  it("links to the project detail page", () => {
    render(
      <MatterCard
        matter={{
          id: "proj-42",
          name: "Annual Report",
          description: null,
          documentCount: 1,
          createdAt: "2026-01-01T00:00:00Z",
          status: "ACTIVE",
        }}
      />,
    );

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/matters/proj-42");
    expect(screen.getByText("1 document")).toBeInTheDocument();
  });

  // Phase 88 624H (G-D19): the client sees the matter status on the list.
  it("shows a Closed badge with the muted slate classes for a CLOSED matter", () => {
    const { container } = render(
      <MatterCard
        matter={{
          id: "proj-closed",
          name: "Estate Late J Mokoena",
          description: null,
          documentCount: 2,
          createdAt: "2026-01-01T00:00:00Z",
          status: "CLOSED",
        }}
      />,
    );

    expect(screen.getByText("Closed")).toBeInTheDocument();
    expect(screen.queryByText("CLOSED")).not.toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-slate-200");
    expect(badge?.className).toContain("text-slate-600");
  });

  it("shows an Active badge for an ACTIVE matter", () => {
    const { container } = render(
      <MatterCard
        matter={{
          id: "proj-active",
          name: "Transfer Erf 1234",
          description: null,
          documentCount: 0,
          createdAt: "2026-01-01T00:00:00Z",
          status: "ACTIVE",
        }}
      />,
    );

    expect(screen.getByText("Active")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-green-100");
  });
});
