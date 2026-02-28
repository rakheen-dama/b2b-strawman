import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EntityPicker } from "@/components/editor/EntityPicker";

// Mock the server actions
const mockFetchProjects = vi.fn();
const mockFetchCustomers = vi.fn();
const mockFetchInvoices = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/templates/actions",
  () => ({
    fetchProjectsForPicker: (...args: unknown[]) => mockFetchProjects(...args),
    fetchCustomersForPicker: (...args: unknown[]) => mockFetchCustomers(...args),
    fetchInvoicesForPicker: (...args: unknown[]) => mockFetchInvoices(...args),
  }),
);

// Mock server-only
vi.mock("server-only", () => ({}));

const sampleProjects = [
  { id: "p1", name: "Alpha Project" },
  { id: "p2", name: "Beta Project" },
];

const sampleCustomers = [
  { id: "c1", name: "Acme Corp", email: "acme@example.com" },
  { id: "c2", name: "Beta Inc", email: "beta@example.com" },
];

const sampleInvoices = [
  { id: "i1", invoiceNumber: "INV-001", customerName: "Acme Corp" },
  { id: "i2", invoiceNumber: null, customerName: "Beta Inc" },
];

beforeEach(() => {
  mockFetchProjects.mockResolvedValue(sampleProjects);
  mockFetchCustomers.mockResolvedValue(sampleCustomers);
  mockFetchInvoices.mockResolvedValue(sampleInvoices);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("EntityPicker", () => {
  it("renders with PROJECT entity type and shows project label", async () => {
    render(
      <EntityPicker
        entityType="PROJECT"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Select a Project for Preview")).toBeInTheDocument();
    });
    expect(mockFetchProjects).toHaveBeenCalled();
  });

  it("renders with CUSTOMER entity type and shows customer label", async () => {
    render(
      <EntityPicker
        entityType="CUSTOMER"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Select a Customer for Preview")).toBeInTheDocument();
    });
    expect(mockFetchCustomers).toHaveBeenCalled();
  });

  it("renders with INVOICE entity type and shows invoice label", async () => {
    render(
      <EntityPicker
        entityType="INVOICE"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Select a Invoice for Preview")).toBeInTheDocument();
    });
    expect(mockFetchInvoices).toHaveBeenCalled();
  });

  it("shows loading state while fetching entities", async () => {
    // Make the fetch hang
    mockFetchProjects.mockReturnValue(new Promise(() => {}));

    render(
      <EntityPicker
        entityType="PROJECT"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Loading...")).toBeInTheDocument();
    });
  });

  it("fires selection callback when entity is selected", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onOpenChange = vi.fn();

    render(
      <EntityPicker
        entityType="PROJECT"
        open={true}
        onOpenChange={onOpenChange}
        onSelect={onSelect}
      />,
    );

    // Wait for entities to load
    await waitFor(() => {
      expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });

    // Open the combobox popover
    const combobox = screen.getByRole("combobox");
    await user.click(combobox);

    // Select "Alpha Project"
    const option = await screen.findByText("Alpha Project");
    await user.click(option);

    // Click the Select button
    const selectButton = screen.getByRole("button", { name: "Select" });
    await user.click(selectButton);

    expect(onSelect).toHaveBeenCalledWith("p1", expect.objectContaining({ id: "p1", name: "Alpha Project" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("shows error state when fetch fails", async () => {
    mockFetchProjects.mockRejectedValue(new Error("Network error"));

    render(
      <EntityPicker
        entityType="PROJECT"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Failed to load projects.")).toBeInTheDocument();
    });
  });

  it("disables Select button when no entity is selected", async () => {
    render(
      <EntityPicker
        entityType="PROJECT"
        open={true}
        onOpenChange={vi.fn()}
        onSelect={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });

    const selectButton = screen.getByRole("button", { name: "Select" });
    expect(selectButton).toBeDisabled();
  });
});
