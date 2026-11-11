import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import fs from "node:fs";
import path from "node:path";
import { DataRequestTimeline } from "@/components/compliance/DataRequestTimeline";
import type { DataRequestResponse } from "@/lib/types";

// G89-F20a: the DSAR detail page is a Server Component. It imported STATUS_CONFIG from the
// "use client" DataRequestTable module, so in the RSC graph it got a client reference instead of the
// object, every lookup missed, and the badge fell back to the raw status ("IN_PROGRESS"). Vitest
// resolves "use client" modules as plain modules, so the bug is pinned on the module graph.

const read = (rel: string) => fs.readFileSync(path.resolve(process.cwd(), rel), "utf8");
const isClientModule = (src: string) => /^\s*["']use client["'];?/.test(src);

afterEach(() => cleanup());

describe("DSAR detail status labels (G89-F20)", () => {
  it("the detail page takes STATUS_CONFIG from a module that is not a client module", () => {
    const page = read("app/(app)/org/[slug]/compliance/requests/[id]/page.tsx");
    expect(isClientModule(page)).toBe(false);
    expect(page).not.toContain('from "@/components/compliance/DataRequestTable"');
    expect(page).toContain('from "@/lib/compliance/data-request-status"');

    expect(isClientModule(read("lib/compliance/data-request-status.ts"))).toBe(false);
  });

  it("STATUS_CONFIG labels every request status", async () => {
    const { STATUS_CONFIG } = await import("@/lib/compliance/data-request-status");
    expect(STATUS_CONFIG.RECEIVED.label).toBe("Received");
    expect(STATUS_CONFIG.IN_PROGRESS.label).toBe("In Progress");
    expect(STATUS_CONFIG.COMPLETED.label).toBe("Completed");
    expect(STATUS_CONFIG.REJECTED.label).toBe("Rejected");
  });

  it("G89-F20b: 'Processing Started' has no date line (no recorded timestamp, no dash)", () => {
    const request = {
      id: "dsar-1",
      clientId: "c-1",
      clientName: "Ndaba Family Trust",
      requestType: "ACCESS",
      status: "IN_PROGRESS",
      description: "Access request",
      rejectionReason: null,
      deadline: "2026-12-06",
      requestedAt: "2026-11-06T08:00:00Z",
      requestedBy: "m-1",
      requestedByName: "Thandi",
      completedAt: null,
      completedBy: null,
      completedByName: null,
      hasExport: false,
      notes: null,
      createdAt: "2026-11-06T08:00:00Z",
    } as DataRequestResponse;

    render(<DataRequestTimeline request={request} />);

    const stage = screen.getByText("Processing Started").parentElement!;
    expect(stage.querySelectorAll("p")).toHaveLength(1);
    expect(stage.textContent).not.toContain("—");
  });
});
