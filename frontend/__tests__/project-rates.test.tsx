import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProjectRatesTab } from "@/components/rates/project-rates-tab";
import type { BillingRate, ProjectMember } from "@/lib/types";

const mockCreateProjectBillingRate = vi.fn();
const mockUpdateProjectBillingRate = vi.fn();
const mockDeleteProjectBillingRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/rate-actions", () => ({
  createProjectBillingRate: (...args: unknown[]) =>
    mockCreateProjectBillingRate(...args),
  updateProjectBillingRate: (...args: unknown[]) =>
    mockUpdateProjectBillingRate(...args),
  deleteProjectBillingRate: (...args: unknown[]) =>
    mockDeleteProjectBillingRate(...args),
}));

function makeMembers(): ProjectMember[] {
  return [
    {
      id: "pm1",
      memberId: "m1",
      name: "Alice Johnson",
      email: "alice@example.com",
      avatarUrl: null,
      projectRole: "lead",
      createdAt: "2025-01-01T00:00:00Z",
    },
    {
      id: "pm2",
      memberId: "m2",
      name: "Bob Smith",
      email: "bob@example.com",
      avatarUrl: null,
      projectRole: "member",
      createdAt: "2025-01-01T00:00:00Z",
    },
  ];
}

function makeProjectBillingRates(): BillingRate[] {
  return [
    {
      id: "pbr1",
      memberId: "m1",
      memberName: "Alice Johnson",
      projectId: "p1",
      projectName: "Project Alpha",
      customerId: null,
      customerName: null,
      scope: "PROJECT_OVERRIDE",
      currency: "USD",
      hourlyRate: 200,
      effectiveFrom: "2025-01-01",
      effectiveTo: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ];
}

describe("ProjectRatesTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no project rate overrides exist", () => {
    render(
      <ProjectRatesTab
        billingRates={[]}
        members={makeMembers()}
        projectId="p1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    expect(screen.getByText("No project rate overrides")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Add billing rate overrides for specific team members on this project.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Add Override/ }),
    ).toBeInTheDocument();
  });

  it("renders project rate overrides table with member data", () => {
    render(
      <ProjectRatesTab
        billingRates={makeProjectBillingRates()}
        members={makeMembers()}
        projectId="p1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    expect(
      screen.getByText("Project Rate Overrides"),
    ).toBeInTheDocument();
    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("$200.00")).toBeInTheDocument();
    expect(screen.getByText("USD")).toBeInTheDocument();
    expect(screen.getByText("Ongoing")).toBeInTheDocument();
  });

  it("opens add project rate override dialog", async () => {
    const user = userEvent.setup();

    render(
      <ProjectRatesTab
        billingRates={[]}
        members={makeMembers()}
        projectId="p1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByRole("button", { name: /Add Override/ }));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Add Project Rate Override" }),
      ).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Member")).toBeInTheDocument();
    expect(screen.getByLabelText("Hourly Rate")).toBeInTheDocument();
    expect(screen.getByLabelText("Effective From")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Create Override" }),
    ).toBeInTheDocument();
  });

  it("delete project rate shows confirmation and calls server action", async () => {
    mockDeleteProjectBillingRate.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <ProjectRatesTab
        billingRates={makeProjectBillingRates()}
        members={makeMembers()}
        projectId="p1"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    const deleteButton = screen.getByLabelText(
      "Delete project rate for Alice Johnson",
    );
    await user.click(deleteButton);

    await waitFor(() => {
      expect(
        screen.getByText("Delete Project Rate Override"),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(mockDeleteProjectBillingRate).toHaveBeenCalledWith(
        "acme",
        "p1",
        "pbr1",
      );
    });
  });
});
