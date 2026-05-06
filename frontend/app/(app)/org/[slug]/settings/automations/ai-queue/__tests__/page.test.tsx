import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockListInvocations = vi.fn();
vi.mock("@/lib/api/ai-invocations", () => ({
  listInvocations: (...args: unknown[]) => mockListInvocations(...args),
}));

vi.mock("@/lib/api/client", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: pushMock,
    refresh: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/org/acme/settings/automations/ai-queue",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import AiQueuePage from "../page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const EMPTY_PAGE = {
  content: [],
  page: { totalElements: 0, totalPages: 0, size: 25, number: 0 },
};

const SAMPLE_PAGE = {
  content: [
    {
      id: "inv-001",
      specialistId: "BILLING",
      invokedBy: "AUTOMATION",
      status: "PENDING_APPROVAL",
      contextEntityType: "invoice",
      contextEntityId: "0c4f0000-0000-0000-0000-0000000000e8",
      createdAt: "2026-04-19T07:14:22Z",
      proposedOutputSummary: "BillingPolishPayload",
      automationActionExecutionId: "ae3-001",
    },
    {
      id: "inv-002",
      specialistId: "INTAKE",
      invokedBy: "MEMBER",
      status: "APPROVED",
      contextEntityType: "customer",
      contextEntityId: "cust-0001-0000-0000-0000-00000000",
      createdAt: "2026-04-18T10:00:00Z",
      proposedOutputSummary: "IntakeExtractionPayload",
      automationActionExecutionId: null,
    },
  ],
  page: { totalElements: 2, totalPages: 1, size: 25, number: 0 },
};

describe("AiQueuePage", () => {
  beforeEach(() => {
    mockListInvocations.mockResolvedValue(SAMPLE_PAGE);
  });

  it("renders queue page with invocations", async () => {
    const page = await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("AI Review Queue")).toBeDefined();
    expect(screen.getByText("Billing")).toBeDefined();
    expect(screen.getByText("Intake")).toBeDefined();
  });

  it("renders empty state when no invocations", async () => {
    mockListInvocations.mockResolvedValue(EMPTY_PAGE);
    const page = await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText(/No pending AI suggestions/)).toBeDefined();
  });

  it("passes status filter from search params to API", async () => {
    await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ status: "PENDING_APPROVAL" }),
    });

    expect(mockListInvocations).toHaveBeenCalledWith(
      expect.objectContaining({ status: "PENDING_APPROVAL" })
    );
  });

  it("passes specialist filter from search params to API", async () => {
    await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ specialistId: "BILLING" }),
    });

    expect(mockListInvocations).toHaveBeenCalledWith(
      expect.objectContaining({ specialistId: "BILLING" })
    );
  });

  it("renders filter controls", async () => {
    const page = await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByTestId("queue-filters")).toBeDefined();
  });

  it("renders 403 error state", async () => {
    const { ApiError } = await import("@/lib/api/client");
    mockListInvocations.mockRejectedValue(new ApiError(403, "Forbidden"));

    const page = await AiQueuePage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText(/Not authorised/)).toBeDefined();
  });
});
