import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/projects",
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
        }}
      />,
    );

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/projects/proj-42");
    expect(screen.getByText("1 document")).toBeInTheDocument();
  });
});
