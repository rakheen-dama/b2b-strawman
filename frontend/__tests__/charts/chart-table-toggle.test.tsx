import { describe, it, expect, afterEach, vi } from "vitest";
import { renderToString } from "react-dom/server";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChartTableToggle, usePrefersReducedMotion } from "@/components/charts/chart-table-toggle";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function MotionProbe() {
  return <span data-testid="motion">{String(usePrefersReducedMotion())}</span>;
}

function mockReducedMotion(reduce: boolean) {
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query: string) =>
      ({
        matches: reduce && query === "(prefers-reduced-motion: reduce)",
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList
  );
}

describe("usePrefersReducedMotion", () => {
  it("assumes reduced motion on the server, before the preference is known", () => {
    mockReducedMotion(false);
    expect(renderToString(<MotionProbe />)).toContain(">true<");
  });

  it("reads the live preference on the client", () => {
    mockReducedMotion(false);
    render(<MotionProbe />);
    expect(screen.getByTestId("motion")).toHaveTextContent("false");
  });
});

const columns = [
  { key: "month", label: "Month" },
  { key: "balance", label: "Balance", numeric: true },
];
const rows = [
  { id: "a", cells: ["Sep 2026", "R 1 000,00"] },
  { id: "b", cells: ["Oct 2026", "R 2 500,00"] },
];

describe("ChartTableToggle", () => {
  it("mounts no table until opened", () => {
    render(<ChartTableToggle caption="Cashbook trend" columns={columns} rows={rows} />);

    expect(screen.queryByRole("table")).toBeNull();
    const button = screen.getByRole("button", { name: "View as table" });
    expect(button).toHaveAttribute("aria-expanded", "false");
  });

  it("mounts exactly one table with the caption, headers and formatted cells", async () => {
    const user = userEvent.setup();
    render(<ChartTableToggle caption="Cashbook trend" columns={columns} rows={rows} />);

    await user.click(screen.getByRole("button", { name: "View as table" }));

    expect(screen.getAllByRole("table")).toHaveLength(1);
    const table = screen.getByRole("table", { name: "Cashbook trend" });
    expect(within(table).getByRole("columnheader", { name: "Month" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Balance" })).toBeInTheDocument();
    expect(within(table).getByRole("rowheader", { name: "Oct 2026" })).toBeInTheDocument();
    expect(within(table).getByRole("cell", { name: "R 2 500,00" })).toBeInTheDocument();

    const hide = screen.getByRole("button", { name: "Hide table" });
    expect(hide).toHaveAttribute("aria-expanded", "true");
    expect(hide).toHaveAttribute("aria-controls", table.closest("[id]")?.id);
  });

  it("unmounts the table on the second click", async () => {
    const user = userEvent.setup();
    render(<ChartTableToggle caption="Cashbook trend" columns={columns} rows={rows} />);

    await user.click(screen.getByRole("button", { name: "View as table" }));
    await user.click(screen.getByRole("button", { name: "Hide table" }));

    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByRole("button", { name: "View as table" })).toHaveAttribute(
      "aria-expanded",
      "false"
    );
  });
});
