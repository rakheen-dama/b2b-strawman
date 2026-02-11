import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import PortalProjectListPage from "@/app/portal/(authenticated)/projects/page";
import type { PortalProject } from "@/lib/types";

// --- Mocks ---

const mockGet = vi.fn();
const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: mockReplace,
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

vi.mock("@/lib/portal-api", () => ({
  portalApi: {
    get: (...args: unknown[]) => mockGet(...args),
    post: vi.fn(),
  },
  isPortalAuthenticated: () => true,
  clearPortalAuth: vi.fn(),
  PortalApiError: class PortalApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
      this.name = "PortalApiError";
    }
  },
}));

const sampleProjects: PortalProject[] = [
  {
    id: "p1",
    name: "Website Redesign",
    description: "Complete overhaul of the company website",
    documentCount: 5,
  },
  {
    id: "p2",
    name: "Brand Guidelines",
    description: null,
    documentCount: 0,
  },
];

describe("PortalProjectListPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders project cards after loading", async () => {
    mockGet.mockResolvedValue(sampleProjects);

    render(<PortalProjectListPage />);

    await waitFor(() => {
      expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    });

    expect(screen.getByText("Brand Guidelines")).toBeInTheDocument();
    expect(screen.getByText("Complete overhaul of the company website")).toBeInTheDocument();
    expect(screen.getByText("5 documents")).toBeInTheDocument();
    expect(screen.getByText("0 documents")).toBeInTheDocument();
  });

  it("renders empty state when no projects", async () => {
    mockGet.mockResolvedValue([]);

    render(<PortalProjectListPage />);

    await waitFor(() => {
      expect(screen.getByText("No projects yet")).toBeInTheDocument();
    });
  });
});
