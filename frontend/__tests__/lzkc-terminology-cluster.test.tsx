import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider } from "@/lib/terminology";
import { TerminologyText } from "@/components/terminology-text";
import { MyWorkHeader } from "@/app/(app)/org/[slug]/my-work/my-work-header";
import { CalendarPageClient } from "@/app/(app)/org/[slug]/calendar/calendar-page-client";

// MyWorkHeader renders DateRangeSelector, which uses the app router.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

// CalendarPageClient loads projects/members via server actions on mount.
vi.mock("@/app/(app)/org/[slug]/calendar/calendar-actions", () => ({
  getCalendarItems: vi.fn(() => Promise.resolve({ items: [], overdueCount: 0 })),
  getCalendarProjects: vi.fn(() => Promise.resolve([])),
  getCalendarMembers: vi.fn(() => Promise.resolve([])),
}));

afterEach(() => {
  cleanup();
});

function withProfile(profile: string | null, ui: React.ReactElement) {
  return render(<TerminologyProvider verticalProfile={profile}>{ui}</TerminologyProvider>);
}

// ---- LZKC-003: indefinite-article token in TerminologyText ----

describe("LZKC-003: TerminologyText {a term} article token", () => {
  it("renders 'an engagement letter' for legal-za (vowel-initial substitution)", () => {
    withProfile(
      "legal-za",
      <TerminologyText template="Create {a proposal} for a client engagement." />
    );
    expect(screen.getByText("Create an engagement letter for a client engagement.")).toBeTruthy();
  });

  it("renders 'a proposal' with no profile", () => {
    withProfile(null, <TerminologyText template="Create {a proposal} for a client engagement." />);
    expect(screen.getByText("Create a proposal for a client engagement.")).toBeTruthy();
  });

  it("keeps 'a' for consonant-initial substitutions (legal-za project -> matter)", () => {
    withProfile("legal-za", <TerminologyText template="Add {a project} first." />);
    expect(screen.getByText("Add a matter first.")).toBeTruthy();
  });

  it("still substitutes plain tokens without an article", () => {
    withProfile("legal-za", <TerminologyText template="No {proposals} yet" />);
    expect(screen.getByText("No engagement letters yet")).toBeTruthy();
  });
});

// ---- LZKC-021: My Work subtitle ----

describe("LZKC-021: My Work header terminology", () => {
  it("legal-za subtitle reads 'action items ... across all matters'", () => {
    withProfile("legal-za", <MyWorkHeader from="2026-07-01" to="2026-07-31" />);
    expect(screen.getByText("Your action items and time tracking across all matters")).toBeTruthy();
  });

  it("default subtitle unchanged without a profile", () => {
    withProfile(null, <MyWorkHeader from="2026-07-01" to="2026-07-31" />);
    expect(screen.getByText("Your tasks and time tracking across all projects")).toBeTruthy();
  });
});

// ---- LZKC-021: Calendar filters ----

describe("LZKC-021: Calendar filter terminology", () => {
  const props = {
    initialItems: [],
    initialOverdueCount: 0,
    initialYear: 2026,
    initialMonth: 7,
    slug: "test-org",
  };

  it("legal-za renders 'All Matters' filter and 'Matters'/'Action Items' type chips", async () => {
    withProfile("legal-za", <CalendarPageClient {...props} />);
    // Select placeholder + hidden option both carry the label
    expect((await screen.findAllByText("All Matters")).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Matters" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Action Items" })).toBeTruthy();
    expect(screen.queryByText("All Projects")).toBeNull();
  });

  it("default renders 'All Projects' filter and 'Tasks'/'Projects' chips without a profile", async () => {
    withProfile(null, <CalendarPageClient {...props} />);
    expect((await screen.findAllByText("All Projects")).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Projects" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Tasks" })).toBeTruthy();
  });
});
