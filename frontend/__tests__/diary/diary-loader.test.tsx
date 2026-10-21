import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 613B — DiaryLoader: requests every kind for the window, resolves "Mine" + admin/owner,
// degrades on failures, rethrows the re-login redirect, and keeps live filters across a refresh.

vi.mock("server-only", () => ({}));

const { mockGetDiary, mockGetMembers, mockGetMatters, mockCaps, mockEmail } = vi.hoisted(() => ({
  mockGetDiary: vi.fn(),
  mockGetMembers: vi.fn(),
  mockGetMatters: vi.fn(),
  mockCaps: vi.fn(),
  mockEmail: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/diary/diary-actions", () => ({
  getDiary: mockGetDiary,
  getDiaryMembers: mockGetMembers,
  getDiaryMatters: mockGetMatters,
}));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/auth", () => ({ getCurrentUserEmail: mockEmail }));
vi.mock("@/components/legal/create-court-date-dialog", () => ({
  CreateCourtDateDialog: () => null,
}));
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
});
