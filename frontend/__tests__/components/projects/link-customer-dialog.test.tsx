import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LinkCustomerDialog } from "@/components/projects/link-customer-dialog";
import type { Customer } from "@/lib/types";

const mockFetchCustomers = vi.fn();
const mockLinkCustomerToProject = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  fetchCustomers: (...args: unknown[]) => mockFetchCustomers(...args),
  linkCustomerToProject: (...args: unknown[]) => mockLinkCustomerToProject(...args),
  unlinkCustomerFromProject: vi.fn().mockResolvedValue({ success: true }),
}));

const allCustomers: Customer[] = [
  {
    id: "c1",
    name: "Acme Corp",
    email: "contact@acme.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m1",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  },
  {
    id: "c2",
    name: "Globex Inc",
    email: "info@globex.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m1",
    createdAt: "2024-02-01T00:00:00Z",
    updatedAt: "2024-02-01T00:00:00Z",
  },
  {
    id: "c3",
    name: "Initech Ltd",
    email: "hello@initech.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m1",
    createdAt: "2024-03-01T00:00:00Z",
    updatedAt: "2024-03-01T00:00:00Z",
  },
];

const existingCustomers: Customer[] = [allCustomers[0]]; // c1 already linked

describe("LinkCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchCustomers.mockResolvedValue(allCustomers);
    mockLinkCustomerToProject.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("fetches customers and shows available ones on open", async () => {
    const user = userEvent.setup();

    render(
      <LinkCustomerDialog
        slug="acme"
        projectId="p1"
        existingCustomers={existingCustomers}
      >
        <button>Open Link Customer Dialog</button>
      </LinkCustomerDialog>,
    );

    await user.click(screen.getByText("Open Link Customer Dialog"));

    await waitFor(() => {
      expect(mockFetchCustomers).toHaveBeenCalledOnce();
    });

    // c1 is already linked, so only c2 and c3 should be visible
    await waitFor(() => {
      expect(screen.getByText("Globex Inc")).toBeInTheDocument();
      expect(screen.getByText("Initech Ltd")).toBeInTheDocument();
    });

    // c1 should NOT be in the list
    expect(screen.queryByText("Acme Corp")).not.toBeInTheDocument();
  });

  it("filters out already-linked customers", async () => {
    const user = userEvent.setup();

    render(
      <LinkCustomerDialog
        slug="acme"
        projectId="p1"
        existingCustomers={existingCustomers}
      >
        <button>Open Link Customer Dialog</button>
      </LinkCustomerDialog>,
    );

    await user.click(screen.getByText("Open Link Customer Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Globex Inc")).toBeInTheDocument();
    });

    // Acme Corp (c1) is already linked — should not appear
    expect(screen.queryByText("Acme Corp")).not.toBeInTheDocument();
  });

  it("calls link action on selection", async () => {
    const user = userEvent.setup();

    render(
      <LinkCustomerDialog
        slug="acme"
        projectId="p1"
        existingCustomers={existingCustomers}
      >
        <button>Open Link Customer Dialog</button>
      </LinkCustomerDialog>,
    );

    await user.click(screen.getByText("Open Link Customer Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Globex Inc")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Globex Inc"));

    await waitFor(() => {
      expect(mockLinkCustomerToProject).toHaveBeenCalledWith("acme", "p1", "c2");
    });
  });
});
