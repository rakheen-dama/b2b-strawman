import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { ProjectStatusFilter } from "@/components/projects/project-status-filter";
import type { Customer } from "@/lib/types";

// --- Mocks ---

const mockCreateProject = vi.fn();
const mockFetchActiveCustomers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/actions", () => ({
  createProject: (...args: unknown[]) => mockCreateProject(...args),
  fetchActiveCustomers: (...args: unknown[]) => mockFetchActiveCustomers(...args),
}));

// Mock next/navigation for ProjectStatusFilter
const mockSearchParams = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
}));

// Mock next/link as a simple anchor
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const activeCustomers: Customer[] = [
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
    name: "Beta Inc",
    email: "info@beta.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m1",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  },
];

describe("ProjectStatusFilter", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders all status filter chips", () => {
    render(<ProjectStatusFilter slug="acme" />);
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Completed")).toBeInTheDocument();
    expect(screen.getByText("Archived")).toBeInTheDocument();
    expect(screen.getByText("All")).toBeInTheDocument();
  });

  it("defaults to Active filter when no status param", () => {
    render(<ProjectStatusFilter slug="acme" />);
    const activeLink = screen.getByText("Active");
    // Active chip should have the active styling class (dark background)
    expect(activeLink.className).toContain("bg-slate-900");
  });

  it("generates correct links for filter chips", () => {
    render(<ProjectStatusFilter slug="acme" />);
    const completedLink = screen.getByText("Completed").closest("a");
    expect(completedLink).toHaveAttribute("href", "/org/acme/projects?status=COMPLETED");
    const archivedLink = screen.getByText("Archived").closest("a");
    expect(archivedLink).toHaveAttribute("href", "/org/acme/projects?status=ARCHIVED");
    const allLink = screen.getByText("All").closest("a");
    expect(allLink).toHaveAttribute("href", "/org/acme/projects?status=ALL");
    // Active should link to base URL (no status param)
    const activeLink = screen.getByText("Active").closest("a");
    expect(activeLink).toHaveAttribute("href", "/org/acme/projects");
  });
});

describe("CreateProjectDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchActiveCustomers.mockResolvedValue(activeCustomers);
    mockCreateProject.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders due date picker when dialog is opened", async () => {
    const user = userEvent.setup();
    render(<CreateProjectDialog slug="acme" />);
    await user.click(screen.getByText("New Project"));
    await waitFor(() => {
      expect(screen.getByLabelText(/due date/i)).toBeInTheDocument();
    });
    const dueDateInput = screen.getByLabelText(/due date/i);
    expect(dueDateInput).toHaveAttribute("type", "date");
  });

  it("renders customer dropdown when dialog is opened", async () => {
    const user = userEvent.setup();
    render(<CreateProjectDialog slug="acme" />);
    await user.click(screen.getByText("New Project"));
    await waitFor(() => {
      expect(screen.getByLabelText(/customer/i)).toBeInTheDocument();
    });
    // Wait for customers to load
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("-- None --")).toBeInTheDocument();
  });

  it("fetches active customers on dialog open", async () => {
    const user = userEvent.setup();
    render(<CreateProjectDialog slug="acme" />);
    await user.click(screen.getByText("New Project"));
    await waitFor(() => {
      expect(mockFetchActiveCustomers).toHaveBeenCalledOnce();
    });
  });
});
