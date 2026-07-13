import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider } from "@/lib/terminology";
import { TerminologyText } from "@/components/terminology-text";
import { TodayTimeEntries } from "@/components/my-work/today-time-entries";
import { createMessages } from "@/lib/messages";

/**
 * LZKC-031 PR-2 — class B: message-catalog terminology residuals.
 *
 * The two enumerated catalog strings (empty-states.json:28 myWork.list.description,
 * :42 timeEntries.list.description) were plain text bypassing the terminology layer.
 * Post-fix they carry {TermKey} placeholders and their consumers render them through
 * <TerminologyText> — legal-za substitutes matters / fee notes, the default profile
 * keeps projects / invoices (identity fallback).
 */

afterEach(() => {
  cleanup();
});

const { t } = createMessages("empty-states");

function withProfile(profile: string | null, ui: React.ReactElement) {
  return render(<TerminologyProvider verticalProfile={profile}>{ui}</TerminologyProvider>);
}

describe("LZKC-031 PR-2 — empty-states.json myWork.list.description (line 28)", () => {
  it("legal-za renders 'across all matters' and 'Head to a matter'", () => {
    withProfile("legal-za", <TerminologyText template={t("myWork.list.description")} />);
    expect(screen.getByText(/across all matters will appear here/)).toBeTruthy();
    expect(screen.getByText(/Head to a matter to create or pick up tasks/)).toBeTruthy();
    expect(screen.queryByText(/\bprojects?\b/)).toBeNull();
  });

  it("default profile keeps 'across all projects' and 'Head to a project'", () => {
    withProfile(null, <TerminologyText template={t("myWork.list.description")} />);
    expect(screen.getByText(/across all projects will appear here/)).toBeTruthy();
    expect(screen.getByText(/Head to a project to create or pick up tasks/)).toBeTruthy();
  });
});

describe("LZKC-031 PR-2 — empty-states.json timeEntries.list.description (line 42) via TodayTimeEntries", () => {
  it("legal-za renders 'generate accurate fee notes'", () => {
    withProfile("legal-za", <TodayTimeEntries entries={[]} />);
    expect(screen.getByText(/generate accurate fee notes/)).toBeTruthy();
    expect(screen.queryByText(/generate accurate invoices/)).toBeNull();
  });

  it("default profile keeps 'generate accurate invoices'", () => {
    withProfile(null, <TodayTimeEntries entries={[]} />);
    expect(screen.getByText(/generate accurate invoices/)).toBeTruthy();
  });
});
