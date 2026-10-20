import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RecentItemsProvider, useRecentItems } from "@/components/recent-items-provider";
import { RecordRecentItem } from "@/components/record-recent-item";

afterEach(() => {
  cleanup();
});

function RecentList() {
  const { items } = useRecentItems();
  return (
    <ul>
      {items.map((item) => (
        <li key={item.href} data-href={item.href}>
          {item.label}
        </li>
      ))}
    </ul>
  );
}

describe("RecordRecentItem (Phase 87 610E)", () => {
  it("records the detail page with its human name and canonical href", () => {
    render(
      <RecentItemsProvider>
        <RecordRecentItem href="/org/t/matters/m-1" label="Smith v Jones" />
        <RecentList />
      </RecentItemsProvider>
    );
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(1);
    expect(items[0].textContent).toBe("Smith v Jones");
    expect(items[0]).toHaveAttribute("data-href", "/org/t/matters/m-1");
  });

  it("records a client page and keeps a single entry per href across re-renders", () => {
    const tree = (label: string) => (
      <RecentItemsProvider>
        <RecordRecentItem href="/org/t/clients/c-1" label={label} />
        <RecentList />
      </RecentItemsProvider>
    );
    const { rerender } = render(tree("Acme Holdings"));
    rerender(tree("Acme Holdings (Pty) Ltd"));
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(1);
    expect(items[0].textContent).toBe("Acme Holdings (Pty) Ltd");
    expect(items[0]).toHaveAttribute("data-href", "/org/t/clients/c-1");
  });

  it("renders nothing itself", () => {
    const { container } = render(
      <RecentItemsProvider>
        <RecordRecentItem href="/org/t/matters/m-2" label="Estate Late Dlamini" />
      </RecentItemsProvider>
    );
    expect(container.innerHTML).toBe("");
  });
});
