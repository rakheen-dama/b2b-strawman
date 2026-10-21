import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 613B — DiaryLoader: requests every kind for the window, resolves "Mine" + admin/owner,
// degrades on failures, rethrows the re-login redirect, and keeps live filters across a refresh.

vi.mock("server-only", () => ({}));

const {
  mockGetDiary,
  mockGetMembers,
  mockGetMatters,
  mockCaps,
  mockEmail,
  mockRedirectToReLogin,
  mockResolveReturnTo,
  MockApiError,
} = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    mockGetDiary: vi.fn(),
    mockGetMembers: vi.fn(),
    mockGetMatters: vi.fn(),
    mockCaps: vi.fn(),
    mockEmail: vi.fn(),
    mockRedirectToReLogin: vi.fn(() => {
      throw new Error("NEXT_REDIRECT");
    }),
    mockResolveReturnTo: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/app/(app)/org/[slug]/diary/diary-actions", () => ({
  getDiary: mockGetDiary,
  getDiaryMembers: mockGetMembers,
  getDiaryMatters: mockGetMatters,
}));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/auth", () => ({ getCurrentUserEmail: mockEmail }));
vi.mock("@/lib/api/client", () => ({
  ApiError: MockApiError,
  resolveServerReturnTo: mockResolveReturnTo,
}));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: mockRedirectToReLogin }));
// 613C: the create dialogs render their trigger label so header-button gating is observable.
vi.mock("@/components/legal/create-court-date-dialog", () => ({
  CreateCourtDateDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button">{triggerLabel}</button>
  ),
}));
vi.mock("@/components/legal/create-prescription-dialog", () => ({
  CreatePrescriptionDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button">{triggerLabel}</button>
  ),
}));
vi.mock("@/components/legal/postpone-dialog", () => ({ PostponeDialog: () => null }));
vi.mock("@/components/legal/outcome-dialog", () => ({ OutcomeDialog: () => null }));
vi.mock("@/components/legal/cancel-court-date-dialog", () => ({
  CancelCourtDateDialog: () => null,
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/task-actions", () => ({ completeTask: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("next/navigation", () => ({
  unstable_rethrow: (error: unknown) => {
    if (error instanceof Error && error.message === "NEXT_REDIRECT") throw error;
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/diary",
}));
vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: React.ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { DiaryLoader, type DiaryLoaderProps } from "@/app/(app)/org/[slug]/diary/diary-loader";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

const PROPS: DiaryLoaderProps = {
  slug: "acme",
  today: "2026-10-21",
  from: "2026-10-19",
  to: "2026-10-25",
  view: "week",
  initialKinds: null,
};

const TASK: DiaryEventResponse = {
  id: "t-1",
  kind: "TASK_DUE",
  date: "2026-10-21",
  title: "Collect medical reports",
  status: "OPEN",
  matterId: "m-1",
  matterName: "Dlamini v Road Accident Fund",
  feeEarnerId: "fe-1",
  feeEarnerName: "Thandi Nkosi",
  overdue: false,
  href: "/matters/m-1",
};

describe("DiaryLoader (613B)", () => {
  beforeEach(() => {
    mockGetDiary.mockResolvedValue({ events: [], typesServed: ["TASK_DUE", "MATTER_DUE"] });
    mockGetMembers.mockResolvedValue([
      { id: "fe-1", name: "Thandi Nkosi", email: "thandi@firm.test" },
    ]);
    mockGetMatters.mockResolvedValue([]);
    mockCaps.mockResolvedValue({ capabilities: [], role: "Owner", isAdmin: false, isOwner: true });
    mockEmail.mockResolvedValue("thandi@firm.test");
    mockResolveReturnTo.mockResolvedValue("/org/acme/diary");
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("requests every kind for the window and renders the week", async () => {
    render(await DiaryLoader(PROPS));
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-19", to: "2026-10-25" });
    expect(screen.getByTestId("diary-range")).toHaveTextContent("19 October to 25 October 2026");
    expect(screen.getByText("Nothing in the diary for this week.")).toBeInTheDocument();
  });

  it("never forwards the ?types= selection to the server", async () => {
    render(await DiaryLoader({ ...PROPS, initialKinds: ["MATTER_DUE"] }));
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-19", to: "2026-10-25" });
  });

  it("preselects Mine for a member whose email matches", async () => {
    mockCaps.mockResolvedValue({
      capabilities: [],
      role: "Member",
      isAdmin: false,
      isOwner: false,
    });
    render(await DiaryLoader(PROPS));
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Mine");
  });

  it("matches the current member's email case-insensitively", async () => {
    mockCaps.mockResolvedValue({
      capabilities: [],
      role: "Member",
      isAdmin: false,
      isOwner: false,
    });
    mockEmail.mockResolvedValue("Thandi@Firm.TEST");
    render(await DiaryLoader(PROPS));
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Mine");
  });

  it("renders the error state when the diary fails", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetDiary.mockRejectedValue(new Error("boom"));
    render(await DiaryLoader(PROPS));
    expect(screen.getByText("Unable to load the diary")).toBeInTheDocument();
  });

  it("renders the error state (no re-login) when the diary fails with a non-401 ApiError", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetDiary.mockRejectedValue(new MockApiError(500, "Internal Server Error"));
    render(await DiaryLoader(PROPS));
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
    expect(screen.getByText("Unable to load the diary")).toBeInTheDocument();
  });

  it("routes an expired-session 401 from the diary to the re-login redirect", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetDiary.mockRejectedValue(new MockApiError(401, "Authentication session expired"));
    await expect(DiaryLoader(PROPS)).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/diary", "expired");
  });

  it("routes a 401 from a secondary fetch (members / matters) to the re-login redirect", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetMatters.mockRejectedValue(new MockApiError(401, "Authentication session expired"));
    await expect(DiaryLoader(PROPS)).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/diary", "expired");
  });

  it("does not swallow the re-login redirect", async () => {
    mockGetDiary.mockRejectedValue(new Error("NEXT_REDIRECT"));
    await expect(DiaryLoader(PROPS)).rejects.toThrow("NEXT_REDIRECT");
  });

  it("keeps fee-earner and matter filters when a refresh re-reads view / types from the URL", async () => {
    const user = userEvent.setup();
    mockGetDiary.mockResolvedValue({ events: [TASK], typesServed: ["TASK_DUE", "MATTER_DUE"] });
    mockGetMatters.mockResolvedValue([
      { id: "m-1", name: "Dlamini v Road Accident Fund", referenceNumber: "LIT-001" },
    ]);
    const { rerender } = render(await DiaryLoader(PROPS));

    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    await user.click(await screen.findByRole("option", { name: "Thandi Nkosi" }));
    await user.click(screen.getByRole("combobox", { name: "Matter" }));
    await user.click(await screen.findByRole("option", { name: /Dlamini v Road Accident Fund/ }));
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Thandi Nkosi");
    expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent(
      "Dlamini v Road Accident Fund"
    );

    // router.refresh() after replaceState: same window, new view / types searchParams.
    rerender(await DiaryLoader({ ...PROPS, view: "list", initialKinds: ["TASK_DUE"] }));

    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Thandi Nkosi");
    expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent(
      "Dlamini v Road Accident Fund"
    );
    expect(screen.getByText("Collect medical reports")).toBeInTheDocument();
  });

  describe("613C", () => {
    const ALL_SERVED = ["COURT_DATE", "PRESCRIPTION", "MATTER_DUE", "TASK_DUE"];
    const MEMBER_CAPS = { role: "Member", isAdmin: false, isOwner: false };

    it("shows the create buttons to an owner when court dates and prescriptions are served", async () => {
      mockGetDiary.mockResolvedValue({ events: [], typesServed: ALL_SERVED });
      render(await DiaryLoader(PROPS));
      expect(screen.getByRole("button", { name: "New court date" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "New prescription tracker" })).toBeInTheDocument();
    });

    it("shows the create buttons to a member holding MANAGE_LEGAL", async () => {
      mockGetDiary.mockResolvedValue({ events: [], typesServed: ALL_SERVED });
      mockCaps.mockResolvedValue({ ...MEMBER_CAPS, capabilities: ["VIEW_LEGAL", "MANAGE_LEGAL"] });
      render(await DiaryLoader(PROPS));
      expect(screen.getByRole("button", { name: "New court date" })).toBeInTheDocument();
    });

    it("hides every write affordance from a VIEW_LEGAL-only member", async () => {
      mockGetDiary.mockResolvedValue({ events: [], typesServed: ALL_SERVED });
      mockCaps.mockResolvedValue({ ...MEMBER_CAPS, capabilities: ["VIEW_LEGAL"] });
      render(await DiaryLoader(PROPS));
      expect(screen.queryByRole("button", { name: "New court date" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Add a court date" })).toBeNull();
    });

    it("fails closed when capabilities cannot be loaded", async () => {
      vi.spyOn(console, "error").mockImplementation(() => {});
      mockGetDiary.mockResolvedValue({ events: [], typesServed: ALL_SERVED });
      mockCaps.mockRejectedValue(new MockApiError(500, "Internal Server Error"));
      render(await DiaryLoader(PROPS));
      expect(screen.queryByRole("button", { name: "New court date" })).toBeNull();
      expect(mockRedirectToReLogin).not.toHaveBeenCalled();
    });

    it("routes a 401 from the capabilities fetch to the re-login redirect", async () => {
      vi.spyOn(console, "error").mockImplementation(() => {});
      mockCaps.mockRejectedValue(new MockApiError(401, "Authentication session expired"));
      await expect(DiaryLoader(PROPS)).rejects.toThrow("NEXT_REDIRECT");
      expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/diary", "expired");
    });

    it("keys the client on the whole window so Week to Day on a Monday remounts", async () => {
      mockGetDiary.mockResolvedValue({ events: [TASK], typesServed: ["TASK_DUE", "MATTER_DUE"] });
      const { rerender } = render(await DiaryLoader(PROPS));
      expect(screen.getByTestId("diary-week-grid")).toBeInTheDocument();
      rerender(await DiaryLoader({ ...PROPS, to: "2026-10-19", view: "day" }));
      expect(screen.queryByTestId("diary-week-grid")).toBeNull();
      expect(screen.getByRole("tab", { name: "Day" })).toHaveAttribute("aria-selected", "true");
    });

    it("seeds valid ?feeEarner= / ?matter= and drops unknown ones", async () => {
      mockGetMembers.mockResolvedValue([
        { id: "fe-1", name: "Thandi Nkosi", email: "thandi@firm.test" },
        { id: "fe-2", name: "Pieter van Wyk", email: "pieter@firm.test" },
      ]);
      mockGetMatters.mockResolvedValue([
        { id: "m-1", name: "Dlamini v Road Accident Fund", referenceNumber: "LIT-001" },
      ]);
      render(await DiaryLoader({ ...PROPS, initialFeeEarner: "fe-2", initialMatterId: "m-1" }));
      expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent(
        "Pieter van Wyk"
      );
      expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent(
        "Dlamini v Road Accident Fund"
      );
      cleanup();

      mockCaps.mockResolvedValue({ ...MEMBER_CAPS, capabilities: [] });
      render(await DiaryLoader({ ...PROPS, initialFeeEarner: "fe-404", initialMatterId: "m-404" }));
      // Unknown member → the role default (Mine for a member); unknown matter → all matters.
      expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Mine");
      expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent("All matters");
    });
  });
});
