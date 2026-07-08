/**
 * LZKC-001 — /pipeline hydration mismatch (dnd-kit aria-describedby drift).
 *
 * Mechanism (verified against @dnd-kit/core@6.3.1 + @dnd-kit/utilities@3.2.2):
 * `DndContext` derives the accessibility target for every draggable via
 * `useUniqueId("DndDescribedBy", id)`. When no `id` prop is given, the id
 * comes from a **module-level incrementing counter** (`ids[prefix]++` in
 * @dnd-kit/utilities), which advances on every DndContext render pass. The
 * SSR pass and the client hydration pass therefore stamp *different* ids
 * (`DndDescribedBy-0` vs `DndDescribedBy-3` in QA's console capture), and
 * every DealCard's `aria-describedby` is a guaranteed hydration mismatch.
 *
 * This is NOT the LZKC-002 radix-useId class (position-derived useId shifted
 * by a divergent shell) — the counter drifts even with an identical tree, so
 * the repro here needs no divergent shell: two render passes of the same
 * element are enough.
 *
 * Fix contract: `<DndContext id="pipeline-board">` — dnd-kit uses the given
 * id verbatim on both passes, so server and client agree.
 */
import { describe, it, expect, vi, afterEach, beforeAll, afterAll } from "vitest";
import { cleanup } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { hydrateRoot, type Root } from "react-dom/client";
import { act } from "react";

declare global {
  // React reads this flag when `act` is called outside @testing-library.

  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(""),
}));

vi.mock("@/app/(app)/org/[slug]/pipeline/actions", () => ({
  intakeDealAction: vi.fn(),
  transitionDealAction: vi.fn(),
  createDealAction: vi.fn(),
}));

import { PipelineBoard } from "@/components/pipeline/PipelineBoard";
import type { DealResponse, StageDto } from "@/lib/api/crm";

const stages: StageDto[] = [
  {
    id: "s1",
    name: "Lead",
    position: 0,
    defaultProbabilityPct: 20,
    stageType: "OPEN",
    archived: false,
  },
  {
    id: "swon",
    name: "Won",
    position: 1,
    defaultProbabilityPct: 100,
    stageType: "WON",
    archived: false,
  },
];

const deal: DealResponse = {
  id: "d1",
  dealNumber: "DEAL-001",
  customerId: "c1",
  title: "Acme website",
  stageId: "s1",
  stageName: "Lead",
  status: "OPEN",
  valueAmount: 10000,
  valueCurrency: "ZAR",
  probabilityPct: null,
  effectiveProbabilityPct: 20,
  weightedValue: 2000,
  expectedCloseDate: "2026-07-01",
  ownerId: "o1",
  source: "Referral",
  wonAt: null,
  lostAt: null,
  lostReason: null,
  customFields: null,
  createdBy: "o1",
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-01T00:00:00Z",
};

function board() {
  return (
    <PipelineBoard
      slug="acme"
      stages={stages}
      deals={[deal]}
      customerNames={{ c1: "Acme Corp" }}
      ownerNames={{ o1: "Alice" }}
      canManage
    />
  );
}

function describedByOf(html: string): string | null {
  return /aria-describedby="([^"]+)"/.exec(html)?.[1] ?? null;
}

describe("LZKC-001 — PipelineBoard dnd-kit describedBy id is SSR-deterministic", () => {
  let prevActEnv: boolean | undefined;
  beforeAll(() => {
    prevActEnv = globalThis.IS_REACT_ACT_ENVIRONMENT;
    globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  });
  afterAll(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = prevActEnv;
  });
  afterEach(() => cleanup());

  it("stamps an identical aria-describedby across two independent render passes", () => {
    // Two renderToString passes stand in for the Fizz pass and the client
    // hydration pass: both consume dnd-kit's shared module state. Pre-fix
    // the counter advances between them (DndDescribedBy-N vs -N+1).
    const firstPass = describedByOf(renderToString(board()));
    const secondPass = describedByOf(renderToString(board()));

    // The draggable DealCard must carry the attribute at all…
    expect(firstPass).not.toBeNull();
    // …and its value must not depend on how many DndContexts rendered
    // before this one.
    expect(secondPass).toBe(firstPass);
  });

  it("hydrates with no aria-describedby mismatch on the deal card", async () => {
    const serverHtml = renderToString(board());
    const ssrDescribedBy = describedByOf(serverHtml);
    expect(ssrDescribedBy).not.toBeNull();

    const container = document.createElement("div");
    document.body.appendChild(container);
    // Self-generated renderToString output — not untrusted input.
    container.innerHTML = serverHtml;

    // React 19 reports attribute-level hydration mismatches through
    // console.error (the dev diff QA captured on /pipeline), NOT through
    // onRecoverableError — capture both channels.
    const recoverableErrors: string[] = [];
    const consoleErrors: string[] = [];
    const errorSpy = vi.spyOn(console, "error").mockImplementation((...args: unknown[]) => {
      consoleErrors.push(args.map(String).join(" "));
    });
    let root: Root | undefined;
    try {
      await act(async () => {
        root = hydrateRoot(container, board(), {
          onRecoverableError: (err) => {
            recoverableErrors.push(String(err instanceof Error ? err.message : err));
          },
        });
      });
    } finally {
      errorSpy.mockRestore();
    }

    // LZKC-001 contract: the client pass regenerates the same describedBy id
    // the server stamped. Pre-fix the counter had advanced and React 19
    // logged the aria-describedby mismatch diff to the console.
    expect(consoleErrors.join("\n")).not.toMatch(/aria-describedby/);
    expect(consoleErrors.join("\n")).not.toMatch(/hydrat/i);
    expect(recoverableErrors.join("\n")).not.toMatch(/hydrat/i);
    const hydrated = container.querySelector("[aria-describedby]");
    expect(hydrated).not.toBeNull();
    expect(hydrated!.getAttribute("aria-describedby")).toBe(ssrDescribedBy);

    await act(async () => {
      root?.unmount();
    });
    container.remove();
  });
});
