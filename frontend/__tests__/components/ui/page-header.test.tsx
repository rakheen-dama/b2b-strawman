import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { PageHeader } from "@b2mash/ui/page-header";

afterEach(() => {
  cleanup();
});

describe("PageHeader", () => {
  it("renders exactly one h1 with the title; eyebrow and description are not headings", () => {
    render(<PageHeader title="Matters" eyebrow="Practice" description="Every open matter" />);

    const h1s = screen.getAllByRole("heading", { level: 1 });
    expect(h1s).toHaveLength(1);
    expect(h1s[0]).toHaveTextContent("Matters");
    expect(screen.getByRole("heading", { level: 1, name: "Matters" })).toBeInTheDocument();
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByText("Practice").tagName).toBe("P");
  });

  it("renders the actions slot and the toolbar row", () => {
    render(
      <PageHeader title="Clients" actions={<button type="button">New Client</button>}>
        <button type="button">Filter</button>
      </PageHeader>
    );

    expect(screen.getByRole("button", { name: "New Client" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Filter" })).toBeInTheDocument();
  });

  it("renders the description", () => {
    render(<PageHeader title="Trust accounting" description="Section 86 trust ledgers" />);
    expect(screen.getByText("Section 86 trust ledgers")).toHaveClass("text-muted-foreground");
  });

  it("defaults data-testid to page-header and passes an override through", () => {
    const { rerender } = render(<PageHeader title="Home" />);
    expect(screen.getByTestId("page-header")).toBeInTheDocument();

    rerender(<PageHeader title="Home" data-testid="dashboard-header" className="mb-6" />);
    expect(screen.queryByTestId("page-header")).toBeNull();
    expect(screen.getByTestId("dashboard-header")).toHaveClass("mb-6");
    // Not a <header>: the app shell owns the page's only banner landmark.
    expect(screen.queryByRole("banner")).toBeNull();
  });
});
