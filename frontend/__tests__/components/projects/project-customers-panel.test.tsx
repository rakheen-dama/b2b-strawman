import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ProjectCustomersPanel } from "@/components/projects/project-customers-panel";
import type { Customer } from "@/lib/types";

// Mock server actions used by LinkCustomerDialog (child component)
vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  fetchCustomers: vi.fn().mockResolvedValue([]),
  linkCustomerToProject: vi.fn().mockResolvedValue({ success: true }),
  unlinkCustomerFromProject: vi.fn().mockResolvedValue({ success: true }),
}));

const mockCustomers: Customer[] = [
  {
    id: "c1",
    name: "Acme Corp",
    email: "contact@acme.com",
    phone: "+1234567890",
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
    status: "ARCHIVED",
    notes: null,
    createdBy: "m1",
    createdAt: "2024-02-01T00:00:00Z",
    updatedAt: "2024-02-01T00:00:00Z",
  },
];

describe("ProjectCustomersPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders customers with correct names", () => {
    render(
      <ProjectCustomersPanel
        customers={mockCustomers}
        slug="acme"
        projectId="p1"
        canManage={false}
      />,
    );

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Globex Inc")).toBeInTheDocument();
  });

  it("renders empty state when no customers", () => {
    render(
      <ProjectCustomersPanel
        customers={[]}
        slug="acme"
        projectId="p1"
        canManage={false}
      />,
    );

    expect(screen.getByText("No linked customers")).toBeInTheDocument();
  });

  it("shows Link Customer button when canManage is true", () => {
    render(
      <ProjectCustomersPanel
        customers={[]}
        slug="acme"
        projectId="p1"
        canManage={true}
      />,
    );

    expect(screen.getByText("Link Customer")).toBeInTheDocument();
  });

  it("hides Link Customer button when canManage is false", () => {
    render(
      <ProjectCustomersPanel
        customers={mockCustomers}
        slug="acme"
        projectId="p1"
        canManage={false}
      />,
    );

    expect(screen.queryByText("Link Customer")).not.toBeInTheDocument();
  });

  it("shows unlink buttons when canManage is true", () => {
    render(
      <ProjectCustomersPanel
        customers={mockCustomers}
        slug="acme"
        projectId="p1"
        canManage={true}
      />,
    );

    const unlinkButtons = screen.getAllByTitle("Unlink customer");
    expect(unlinkButtons).toHaveLength(2);
  });

  it("hides unlink buttons when canManage is false", () => {
    render(
      <ProjectCustomersPanel
        customers={mockCustomers}
        slug="acme"
        projectId="p1"
        canManage={false}
      />,
    );

    expect(screen.queryByTitle("Unlink customer")).not.toBeInTheDocument();
  });

  it("shows customer count badge", () => {
    render(
      <ProjectCustomersPanel
        customers={mockCustomers}
        slug="acme"
        projectId="p1"
        canManage={false}
      />,
    );

    expect(screen.getByText("2")).toBeInTheDocument();
  });
});
