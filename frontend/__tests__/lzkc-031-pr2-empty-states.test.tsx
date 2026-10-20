import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { createMessages } from "@/lib/messages";
import emptyStates from "@/lib/messages/en/empty-states.json";
import help from "@/lib/messages/en/help.json";
import gettingStarted from "@/lib/messages/en/getting-started.json";

/**
 * LZKC-031 PR-2 — message-catalog copy is literal legal English (Phase 87 611C, ADR-338).
 * The catalogs carry no {TermKey} tokens; consumers render the catalog string directly.
 */

afterEach(() => {
  cleanup();
});

const { t } = createMessages("empty-states");

function values(obj: unknown): string[] {
  if (typeof obj === "string") return [obj];
  if (obj && typeof obj === "object") return Object.values(obj).flatMap(values);
  return [];
}

describe("message catalogs — no terminology tokens", () => {
  // Durable guard (ADR-338): the JSON message catalogues carry no runtime {token} substitution. This is the
  // catalogue half of the single-vocabulary enforcement — no-terminology-remnants.test.ts scans .ts/.tsx source.
  // Single-brace {token} (the deleted runtime-substitution template syntax); {{var}} interpolation is allowed.
  const SINGLE_BRACE_TOKEN = /(?<!\{)\{[A-Za-z][\w ]*\}(?!\})/;

  it.each([
    ["empty-states", emptyStates as unknown],
    ["help", help as unknown],
    ["getting-started", gettingStarted as unknown],
  ])("%s.json has no {Token} placeholders", (_ns, catalog) => {
    expect(values(catalog).filter((v) => SINGLE_BRACE_TOKEN.test(v))).toEqual([]);
  });
});

describe("empty-states.json myWork.list.description", () => {
  it("reads 'across all matters' and 'Head to a matter'", () => {
    const copy = t("myWork.list.description");
    expect(copy).toContain("across all matters will appear here");
    expect(copy).toContain("Head to a matter to create or pick up tasks");
    expect(copy).not.toMatch(/\bprojects?\b/);
  });
});

describe("TodayTimeEntries empty state", () => {
  it("renders 'No time recordings yet' and 'generate accurate fee notes'", () => {
    render(<TodayTimeEntries entries={[]} />);
    expect(screen.getByText("No time recordings yet")).toBeInTheDocument();
    expect(screen.getByText(/generate accurate fee notes/)).toBeInTheDocument();
    expect(screen.queryByText(/generate accurate invoices/)).toBeNull();
  });
});
