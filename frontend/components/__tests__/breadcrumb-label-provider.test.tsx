import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import {
  BreadcrumbLabel,
  BreadcrumbLabelProvider,
  useBreadcrumbLabels,
} from "@/components/breadcrumb-label-provider";
import { RecentItemsProvider, useRecentItems } from "@/components/recent-items-provider";
import { RecordRecentItem } from "@/components/record-recent-item";

afterEach(() => {
  cleanup();
});

function Labels() {
  const labels = useBreadcrumbLabels();
  return (
    <ul aria-label="labels">
      {[...labels].map(([href, label]) => (
        <li key={href} data-href={href}>
          {label}
        </li>
      ))}
    </ul>
  );
}

function RecentList() {
  const { items } = useRecentItems();
  return (
    <ul aria-label="recent">
      {items.map((item) => (
        <li key={item.href}>{item.label}</li>
      ))}
    </ul>
  );
}

describe("BreadcrumbLabelProvider (Phase 88 628B)", () => {
  it("holds the label a BreadcrumbLabel publishes and drops it on unmount", () => {
    const tree = (mounted: boolean) => (
      <BreadcrumbLabelProvider>
        {mounted && <BreadcrumbLabel href="/org/t/invoices/i-1" label="INV-0002" />}
        <Labels />
      </BreadcrumbLabelProvider>
    );
    const { rerender } = render(tree(true));
    const item = screen.getByText("INV-0002");
    expect(item).toHaveAttribute("data-href", "/org/t/invoices/i-1");
    rerender(tree(false));
    expect(screen.queryByText("INV-0002")).not.toBeInTheDocument();
  });

  it("updates the label when the publisher's label changes", () => {
    const tree = (label: string) => (
      <BreadcrumbLabelProvider>
        <BreadcrumbLabel href="/org/t/matters/m-1" label={label} />
        <Labels />
      </BreadcrumbLabelProvider>
    );
    const { rerender } = render(tree("Smith v Jones"));
    rerender(tree("Smith v Jones (appeal)"));
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    expect(screen.getByText("Smith v Jones (appeal)")).toBeInTheDocument();
  });

  it("is a no-op without the provider (publisher renders nothing, consumer sees no labels)", () => {
    const { container } = render(
      <>
        <BreadcrumbLabel href="/org/t/matters/m-1" label="Smith v Jones" />
        <Labels />
      </>
    );
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
    expect(container.textContent).toBe("");
  });

  it("RecordRecentItem publishes to both the Recent list and the breadcrumb labels", () => {
    render(
      <RecentItemsProvider>
        <BreadcrumbLabelProvider>
          <RecordRecentItem href="/org/t/clients/c-1" label="Acme Holdings" />
          <Labels />
          <RecentList />
        </BreadcrumbLabelProvider>
      </RecentItemsProvider>
    );
    expect(
      screen.getByRole("list", { name: "labels" }).querySelector('[data-href="/org/t/clients/c-1"]')
        ?.textContent
    ).toBe("Acme Holdings");
    expect(screen.getByRole("list", { name: "recent" }).textContent).toBe("Acme Holdings");
  });

  it("BreadcrumbLabel does not add to the Recent list", () => {
    render(
      <RecentItemsProvider>
        <BreadcrumbLabelProvider>
          <BreadcrumbLabel href="/org/t/reports/aging" label="Invoice Aging Report" />
          <RecentList />
        </BreadcrumbLabelProvider>
      </RecentItemsProvider>
    );
    expect(screen.getByRole("list", { name: "recent" }).textContent).toBe("");
  });
});
