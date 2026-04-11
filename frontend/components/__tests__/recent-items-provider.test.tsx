import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, act } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));

import { usePathname } from "next/navigation";
import { RecentItemsProvider, useRecentItems } from "@/components/recent-items-provider";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// Helper component to expose context values via test IDs
function TestConsumer() {
  const { items, addItem } = useRecentItems();
  return (
    <div>
      <div data-testid="item-count">{items.length}</div>
      <ul>
        {items.map((item) => (
          <li key={item.href} data-testid={`item-${item.href}`}>
            {item.label}
          </li>
        ))}
      </ul>
      <button onClick={() => addItem({ href: "/org/test-org/projects/1", label: "Project One" })}>
        add project one
      </button>
      <button onClick={() => addItem({ href: "/org/test-org/projects/2", label: "Project Two" })}>
        add project two
      </button>
      <button onClick={() => addItem({ href: "/org/test-org/projects/3", label: "Project Three" })}>
        add project three
      </button>
      <button onClick={() => addItem({ href: "/org/test-org/projects/4", label: "Project Four" })}>
        add project four
      </button>
      <button onClick={() => addItem({ href: "/org/test-org/projects/5", label: "Project Five" })}>
        add project five
      </button>
      <button onClick={() => addItem({ href: "/org/test-org/projects/6", label: "Project Six" })}>
        add project six
      </button>
    </div>
  );
}

function renderProvider() {
  return render(
    <RecentItemsProvider>
      <TestConsumer />
    </RecentItemsProvider>
  );
}

describe("RecentItemsProvider", () => {
  it("starts with empty items list", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderProvider();
    expect(screen.getByTestId("item-count").textContent).toBe("0");
  });

  it("addItem prepends new item to the list", async () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    const { getByText, getByTestId } = renderProvider();

    await act(async () => {
      getByText("add project one").click();
    });
    await act(async () => {
      getByText("add project two").click();
    });

    expect(getByTestId("item-count").textContent).toBe("2");
    // Project Two was added last, should be first (prepend)
    const items = screen.getAllByRole("listitem");
    expect(items[0].textContent).toBe("Project Two");
    expect(items[1].textContent).toBe("Project One");
  });

  it("addItem deduplicates by href — re-adding existing href moves it to front", async () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    const { getByText, getByTestId } = renderProvider();

    await act(async () => {
      getByText("add project one").click();
    });
    await act(async () => {
      getByText("add project two").click();
    });
    // Re-add project one — should move to front, not duplicate
    await act(async () => {
      getByText("add project one").click();
    });

    expect(getByTestId("item-count").textContent).toBe("2");
    const items = screen.getAllByRole("listitem");
    expect(items[0].textContent).toBe("Project One");
    expect(items[1].textContent).toBe("Project Two");
  });

  it("addItem keeps max 5 items — 6th item pushes oldest out", async () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    const { getByText, getByTestId } = renderProvider();

    await act(async () => {
      getByText("add project one").click();
    });
    await act(async () => {
      getByText("add project two").click();
    });
    await act(async () => {
      getByText("add project three").click();
    });
    await act(async () => {
      getByText("add project four").click();
    });
    await act(async () => {
      getByText("add project five").click();
    });
    await act(async () => {
      getByText("add project six").click();
    });

    expect(getByTestId("item-count").textContent).toBe("5");
    // Project Six is at front; Project One was pushed out
    const items = screen.getAllByRole("listitem");
    expect(items[0].textContent).toBe("Project Six");
    expect(items.find((el) => el.textContent === "Project One")).toBeUndefined();
  });
});
