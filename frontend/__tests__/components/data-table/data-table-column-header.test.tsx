import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { DataTable } from "@/components/data-table/data-table";
import { SORTABLE_HEAD_CLASS } from "@/components/data-table/data-table-column-header";
import { defineColumns, type DataTableSort } from "@/components/data-table/define-columns";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/matters",
}));

// `scroll` / `prefetch` are next/link props, not DOM attributes: drop them before spreading.
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => {
    const anchorProps = { ...props };
    delete anchorProps.scroll;
    delete anchorProps.prefetch;
    return (
      <a href={href} {...anchorProps}>
        {children}
      </a>
    );
  },
}));

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

interface Row {
  id: string;
  reference: string;
  amount: number;
}

const ROWS: Row[] = [
  { id: "a", reference: "MAT-001", amount: 10 },
  { id: "b", reference: "MAT-002", amount: 20 },
];

const columns = defineColumns<Row>([
  {
    id: "reference",
    meta: { label: "Reference", sortKey: "reference" },
    cell: ({ row }) => row.original.reference,
  },
  {
    id: "amount",
    meta: { label: "Amount", sortKey: "amount", align: "end" },
    cell: ({ row }) => row.original.amount,
  },
]);

function renderWithSort(sort: DataTableSort) {
  return render(
    <DataTable
      columns={columns}
      data={ROWS}
      getRowId={(row) => row.id}
      empty={<p>No header fixture rows.</p>}
      sort={sort}
    />
  );
}

const header = (name: string) => screen.getByRole("columnheader", { name });
const icon = (name: string) => header(name).querySelector("svg") as SVGElement;
/** The icon's class list minus the opacity, i.e. its slot. */
const slotClasses = (svg: SVGElement) =>
  Array.from(svg.classList)
    .filter((c) => !c.startsWith("opacity-") && !c.startsWith("lucide"))
    .sort();

const urlSort = (key: string | null, dir: "asc" | "desc" = "asc"): DataTableSort => ({
  key,
  dir,
  hrefFor: (sortKey) => `/x?sort=${sortKey}`,
});

describe("DataTableColumnHeader — F1 sort icon slot (K-06)", () => {
  it("the inactive chevron stays visible at opacity-40 but out of flow, in the th's reserved padding", () => {
    renderWithSort(urlSort("amount"));
    const svg = icon("Reference");
    expect(svg).toHaveClass("lucide-chevrons-up-down", "opacity-40", "absolute");
    expect(svg).toHaveAttribute("aria-hidden", "true");
    expect(svg).not.toHaveClass("hidden");
    expect(header("Reference")).toHaveClass(...SORTABLE_HEAD_CLASS.split(" "));
  });

  it("the active and inactive icons share one absolute slot, so sorting never shifts the column", () => {
    const { rerender } = renderWithSort(urlSort("amount"));
    const inactive = slotClasses(icon("Reference"));
    const thInactive = header("Reference").className;

    rerender(
      <DataTable
        columns={columns}
        data={ROWS}
        getRowId={(row) => row.id}
        empty={<p>No header fixture rows.</p>}
        sort={urlSort("reference", "desc")}
      />
    );
    const active = icon("Reference");
    expect(active).toHaveClass("lucide-arrow-down");
    expect(active).not.toHaveClass("opacity-40");
    expect(slotClasses(active)).toEqual(inactive);
    expect(inactive).toContain("absolute");
    // The reserved padding is identical in both states (the th's classes do not change).
    expect(header("Reference").className).toBe(thInactive);
    expect(header("Reference")).toHaveAttribute("aria-sort", "descending");
  });

  it("URL sort: the link's name is the label, it has no inline gap, and the icon sits inside it", () => {
    renderWithSort(urlSort("reference"));
    const link = within(header("Amount")).getByRole("link", { name: "Amount" });
    expect(link).toHaveAttribute("href", "/x?sort=amount");
    expect(link).not.toHaveClass("gap-1");
    expect(link.contains(icon("Amount"))).toBe(true);
    // Right-aligned headers keep the icon in the same right-hand slot.
    expect(header("Amount")).toHaveClass("text-right", "relative", "pr-5");
    expect(header("Amount")).toHaveAttribute("aria-sort", "none");
  });

  it("client sort: the button's name is the label, and clicking the icon still sorts", () => {
    const onSort = vi.fn();
    renderWithSort({ key: "reference", dir: "asc", onSort });
    const button = within(header("Amount")).getByRole("button", { name: "Amount" });
    expect(button).not.toHaveClass("gap-1");
    const svg = icon("Amount");
    expect(button.contains(svg)).toBe(true);
    expect(slotClasses(svg)).toEqual(slotClasses(icon("Reference")));

    fireEvent.click(svg);
    expect(onSort).toHaveBeenCalledWith("amount");
    expect(header("Amount")).toHaveClass("relative", "pr-5");
    expect(header("Reference")).toHaveAttribute("aria-sort", "ascending");
  });
});
