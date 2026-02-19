import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { FileText } from "lucide-react";
import { ActionCard } from "@/components/setup/action-card";

describe("ActionCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders title, description, and icon", () => {
    render(
      <ActionCard
        icon={FileText}
        title="Generate Invoice"
        description="Create an invoice from unbilled time entries."
      />,
    );

    expect(screen.getByText("Generate Invoice")).toBeInTheDocument();
    expect(
      screen.getByText("Create an invoice from unbilled time entries."),
    ).toBeInTheDocument();
  });

  it("renders primary and secondary action links", () => {
    render(
      <ActionCard
        icon={FileText}
        title="Generate Invoice"
        description="Create an invoice from unbilled time entries."
        primaryAction={{ label: "Create Invoice", href: "/invoices/new" }}
        secondaryAction={{ label: "View All", href: "/invoices" }}
      />,
    );

    const createLink = screen.getByRole("link", { name: "Create Invoice" });
    expect(createLink).toHaveAttribute("href", "/invoices/new");

    const viewLink = screen.getByRole("link", { name: "View All" });
    expect(viewLink).toHaveAttribute("href", "/invoices");
  });

  it("applies accent variant class", () => {
    const { container } = render(
      <ActionCard
        icon={FileText}
        title="Action"
        description="Description"
        variant="accent"
      />,
    );

    const card = container.querySelector("[data-slot='card']");
    expect(card?.className).toMatch(/bg-teal/);
  });
});
