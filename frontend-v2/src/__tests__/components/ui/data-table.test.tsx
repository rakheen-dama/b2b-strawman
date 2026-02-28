import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { type ColumnDef } from "@tanstack/react-table";

import { DataTable } from "@/components/ui/data-table";
import { DataTableEmpty } from "@/components/ui/data-table-empty";

afterEach(() => cleanup());

interface TestRow {
  id: number;
  name: string;
  email: string;
}

const testData: TestRow[] = [
  { id: 1, name: "Alice Johnson", email: "alice@example.com" },
  { id: 2, name: "Bob Smith", email: "bob@example.com" },
  { id: 3, name: "Carol Williams", email: "carol@example.com" },
];

const testColumns: ColumnDef<TestRow, unknown>[] = [
  {
    accessorKey: "name",
    header: "Name",
  },
  {
    accessorKey: "email",
    header: "Email",
  },
];

describe("DataTable", () => {
  it("renders columns and data correctly", () => {
    render(<DataTable columns={testColumns} data={testData} />);

    // Headers
    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Email")).toBeInTheDocument();

    // Data rows
    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("bob@example.com")).toBeInTheDocument();
    expect(screen.getByText("Carol Williams")).toBeInTheDocument();
  });

  it("calls onRowClick when row is clicked", async () => {
    const user = userEvent.setup();
    const handleRowClick = vi.fn();

    render(
      <DataTable
        columns={testColumns}
        data={testData}
        onRowClick={handleRowClick}
      />
    );

    await user.click(screen.getByText("Alice Johnson"));

    expect(handleRowClick).toHaveBeenCalledTimes(1);
    expect(handleRowClick).toHaveBeenCalledWith(testData[0]);
  });

  it("shows skeleton when isLoading is true", () => {
    const { container } = render(
      <DataTable columns={testColumns} data={[]} isLoading={true} />
    );

    // DataTableSkeleton renders Skeleton divs with data-slot="skeleton"
    const skeletons = container.querySelectorAll('[data-slot="skeleton"]');
    expect(skeletons.length).toBeGreaterThan(0);

    // Should not render actual data
    expect(screen.queryByText("Alice Johnson")).not.toBeInTheDocument();
  });

  it("shows empty state when data is empty", () => {
    render(
      <DataTable
        columns={testColumns}
        data={[]}
        emptyState={
          <DataTableEmpty
            title="No results found"
            description="Try adjusting your search."
          />
        }
      />
    );

    expect(screen.getByText("No results found")).toBeInTheDocument();
    expect(
      screen.getByText("Try adjusting your search.")
    ).toBeInTheDocument();
  });

  it("renders sorting headers that are interactive", async () => {
    const user = userEvent.setup();
    const handleSortingChange = vi.fn();

    render(
      <DataTable
        columns={testColumns}
        data={testData}
        sorting={[]}
        onSortingChange={handleSortingChange}
      />
    );

    // Sortable columns should have button wrappers
    const nameHeader = screen.getByText("Name");
    const button = nameHeader.closest("button");
    expect(button).toBeInTheDocument();

    // Click the sort button
    await user.click(button!);
    expect(handleSortingChange).toHaveBeenCalled();
  });

  it("applies cursor-pointer class when onRowClick is provided", () => {
    const { container } = render(
      <DataTable
        columns={testColumns}
        data={testData}
        onRowClick={() => {}}
      />
    );

    const dataRows = container.querySelectorAll("tbody tr");
    expect(dataRows.length).toBe(3);
    dataRows.forEach((row) => {
      expect(row.className).toContain("cursor-pointer");
    });
  });

  it("shows 'No results.' when data is empty and no emptyState provided", () => {
    render(<DataTable columns={testColumns} data={[]} />);

    expect(screen.getByText("No results.")).toBeInTheDocument();
  });
});
