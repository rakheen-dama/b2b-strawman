/**
 * LZKC-024 — StageReorder hydration mismatch (dnd-kit aria-describedby drift).
 *
 * Same class as LZKC-001 on PipelineBoard (see
 * __tests__/pipeline/pipeline-board-hydration.test.tsx for the full
 * mechanism write-up). Short version: `DndContext` without an `id` prop
 * derives every sortable row's `aria-describedby` from a module-level
 * incrementing counter in @dnd-kit/utilities. The counter advances on every
 * DndContext render pass, so the SSR pass and the client hydration pass
 * stamp different ids (`DndDescribedBy-N` vs `DndDescribedBy-N+1`) — a
 * guaranteed hydration mismatch on every stage row.
 *
 * Fix contract: `<DndContext id="stage-reorder">` — dnd-kit uses the given
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

import { StageReorder } from "@/components/settings/StageReorder";
import type { StageDto } from "@/lib/api/crm";

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
    id: "s2",
    name: "Qualified",
    position: 1,
    defaultProbabilityPct: 40,
    stageType: "OPEN",
    archived: false,
  },
];

function reorderList() {
  return (
    <StageReorder stages={stages} onReorder={() => {}} renderActions={() => <span>actions</span>} />
  );
}

function describedByOf(html: string): string | null {
  return /aria-describedby="([^"]+)"/.exec(html)?.[1] ?? null;
}

describe("LZKC-024 — StageReorder dnd-kit describedBy id is SSR-deterministic", () => {
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
    const firstPass = describedByOf(renderToString(reorderList()));
    const secondPass = describedByOf(renderToString(reorderList()));

    // The sortable stage row must carry the attribute at all…
    expect(firstPass).not.toBeNull();
    // …and its value must not depend on how many DndContexts rendered
    // before this one.
    expect(secondPass).toBe(firstPass);
  });

  it("hydrates with no aria-describedby mismatch on the stage row", async () => {
    const serverHtml = renderToString(reorderList());
    const ssrDescribedBy = describedByOf(serverHtml);
    expect(ssrDescribedBy).not.toBeNull();

    const container = document.createElement("div");
    document.body.appendChild(container);
    // Self-generated renderToString output — not untrusted input.
    container.innerHTML = serverHtml;

    // React 19 reports attribute-level hydration mismatches through
    // console.error, NOT through onRecoverableError — capture both channels.
    const recoverableErrors: string[] = [];
    const consoleErrors: string[] = [];
    const errorSpy = vi.spyOn(console, "error").mockImplementation((...args: unknown[]) => {
      consoleErrors.push(args.map(String).join(" "));
    });
    let root: Root | undefined;
    try {
      await act(async () => {
        root = hydrateRoot(container, reorderList(), {
          onRecoverableError: (err) => {
            recoverableErrors.push(String(err instanceof Error ? err.message : err));
          },
        });
      });
    } finally {
      errorSpy.mockRestore();
    }

    // LZKC-024 contract: the client pass regenerates the same describedBy id
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
