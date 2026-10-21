import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 87 613B — /diary server page: ?from= snaps to Monday, ?view= falls back to week,
// all kinds are requested from the server, and the loader resolves "Mine" + admin/owner.

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

import DiaryPage from "@/app/(app)/org/[slug]/diary/page";
import { DiaryListSkeleton } from "@/components/diary/diary-list-view";
import { DiaryWeekSkeleton } from "@/components/diary/diary-week-view";

type El = React.ReactElement<Record<string, unknown>>;

/** Calls the page, then resolves the async loader inside its <Suspense> boundary. */
async function renderPage(search: Record<string, string> = {}) {
  const page = (await DiaryPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(search),
  })) as El;
  const children = React.Children.toArray(page.props.children as React.ReactNode) as El[];
  const suspense = children.find((c) => c.type === React.Suspense)!;
  const loader = suspense.props.children as El;
  const loaderFn = loader.type as (p: Record<string, unknown>) => Promise<React.ReactElement>;
  const loaded = await loaderFn(loader.props);
  render(
    <>
      {children.filter((c) => c !== suspense)}
      {loaded}
    </>
  );
  return { suspense, loader };
}

describe("Diary page (613B)", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 21, 10, 0, 0)); // Wed 21 Oct 2026, local
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
    vi.useRealTimers();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("defaults to the current Mon–Sun week and requests every kind", async () => {
    const { loader } = await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Diary" })).toBeInTheDocument();
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-19", to: "2026-10-25" });
    expect(loader.props).toMatchObject({ view: "week", initialKinds: [] });
    expect(screen.getByTestId("diary-range")).toHaveTextContent("19 October to 25 October 2026");
  });

  it("snaps ?from= to its Monday", async () => {
    await renderPage({ from: "2026-11-01" }); // Sunday
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-26", to: "2026-11-01" });
  });

  it("falls back to the current week for an invalid ?from=", async () => {
    await renderPage({ from: "2026-02-31" });
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-19", to: "2026-10-25" });
  });

  it("uses the view's skeleton as the Suspense fallback", async () => {
    const week = await renderPage({ view: "month" });
    expect((week.suspense.props.fallback as El).type).toBe(DiaryWeekSkeleton);
    expect(week.loader.props.view).toBe("week");
    cleanup();
    const list = await renderPage({ view: "list" });
    expect((list.suspense.props.fallback as El).type).toBe(DiaryListSkeleton);
  });

  it("keeps ?types= client-side and never forwards it to the server", async () => {
    const { loader } = await renderPage({ types: "PRESCRIPTION,BOGUS" });
    expect(mockGetDiary).toHaveBeenCalledWith({ from: "2026-10-19", to: "2026-10-25" });
    expect(loader.props.initialKinds).toEqual(["PRESCRIPTION"]);
  });

  it("preselects Mine for a member whose email matches", async () => {
    mockCaps.mockResolvedValue({
      capabilities: [],
      role: "Member",
      isAdmin: false,
      isOwner: false,
    });
    await renderPage();
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Mine");
  });

  it("renders the error state when the diary fails, keeping the page", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockGetDiary.mockRejectedValue(new Error("boom"));
    await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Diary" })).toBeInTheDocument();
    expect(screen.getByText("Unable to load the diary")).toBeInTheDocument();
  });

  it("does not swallow the re-login redirect", async () => {
    mockGetDiary.mockRejectedValue(new Error("NEXT_REDIRECT"));
    await expect(renderPage()).rejects.toThrow("NEXT_REDIRECT");
  });
});
