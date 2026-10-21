import React, { use } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 87 613B — /diary server page: ?from= snaps to Monday, ?view= falls back to week, ?types=
// absent vs present-but-empty, and the view's skeleton as the Suspense fallback. The loader is
// stubbed (its own tests live in diary-loader.test.tsx); the stub renders what the page handed it.

const { loaderState } = vi.hoisted(() => ({ loaderState: { suspend: false } }));
const NEVER = new Promise<never>(() => {});

vi.mock("@/app/(app)/org/[slug]/diary/diary-loader", () => ({
  DiaryLoader: (props: {
    from: string;
    to: string;
    view: string;
    initialKinds: string[] | null;
  }) => {
    if (loaderState.suspend) use(NEVER);
    return (
      <div
        data-testid="diary-loader"
        data-from={props.from}
        data-to={props.to}
        data-view={props.view}
        data-kinds={props.initialKinds === null ? "ALL" : props.initialKinds.join(",")}
      />
    );
  },
}));

import DiaryPage from "@/app/(app)/org/[slug]/diary/page";

async function renderPage(search: Record<string, string> = {}) {
  render(
    await DiaryPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve(search),
    })
  );
  return screen.queryByTestId("diary-loader");
}

describe("Diary page (613B)", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 21, 10, 0, 0)); // Wed 21 Oct 2026, local
    loaderState.suspend = false;
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("defaults to the current Mon–Sun week, the Week view and every kind", async () => {
    const loader = await renderPage();
    expect(screen.getByRole("heading", { level: 1, name: "Diary" })).toBeInTheDocument();
    expect(loader).toHaveAttribute("data-from", "2026-10-19");
    expect(loader).toHaveAttribute("data-to", "2026-10-25");
    expect(loader).toHaveAttribute("data-view", "week");
    expect(loader).toHaveAttribute("data-kinds", "ALL");
  });

  it("snaps ?from= to its Monday", async () => {
    const loader = await renderPage({ from: "2026-11-01" }); // Sunday
    expect(loader).toHaveAttribute("data-from", "2026-10-26");
    expect(loader).toHaveAttribute("data-to", "2026-11-01");
  });

  it("falls back to the current week for an invalid ?from=", async () => {
    const loader = await renderPage({ from: "2026-02-31" });
    expect(loader).toHaveAttribute("data-from", "2026-10-19");
  });

  it("falls back to the Week view for an unsupported ?view=", async () => {
    expect(await renderPage({ view: "month" })).toHaveAttribute("data-view", "week");
    cleanup();
    expect(await renderPage({ view: "list" })).toHaveAttribute("data-view", "list");
  });

  it("parses ?types= to known kinds", async () => {
    const loader = await renderPage({ types: "PRESCRIPTION,BOGUS" });
    expect(loader).toHaveAttribute("data-kinds", "PRESCRIPTION");
  });

  it("treats an empty ?types= as no kinds selected, not every kind", async () => {
    const loader = await renderPage({ types: "" });
    expect(loader).toHaveAttribute("data-kinds", "");
  });

  it("uses the view's skeleton as the Suspense fallback", async () => {
    loaderState.suspend = true;
    await renderPage({ view: "month" });
    expect(screen.getByTestId("diary-week-skeleton")).toBeInTheDocument();
    cleanup();
    await renderPage({ view: "list" });
    expect(screen.getByTestId("diary-list-skeleton")).toBeInTheDocument();
  });
});
