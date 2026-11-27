import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { FeeNoteStatusFilter } from "@/components/invoices/fee-note-status-filter";

afterEach(() => {
  cleanup();
});

// The exact strings the template-literal joins rendered before the switch to `cn()`.
const PILL = "rounded-full px-3 py-1 text-sm transition-colors";
const ACTIVE = `${PILL} bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900`;
const IDLE = `${PILL} bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700`;

describe("FeeNoteStatusFilter", () => {
  it("renders the same pill classes as before (cn drops no class)", () => {
    render(
      <FeeNoteStatusFilter slug="acme" statusOptions={["DRAFT", "SENT"]} activeStatus="SENT" />
    );
    expect(screen.getByRole("link", { name: "All" })).toHaveAttribute("class", IDLE);
    expect(screen.getByRole("link", { name: "Draft" })).toHaveAttribute("class", IDLE);
    expect(screen.getByRole("link", { name: "Sent" })).toHaveAttribute("class", ACTIVE);
  });

  it("keeps the hrefs and marks All active when no status is set", () => {
    render(<FeeNoteStatusFilter slug="acme" statusOptions={["DRAFT"]} />);
    expect(screen.getByRole("link", { name: "All" })).toHaveAttribute("href", "/org/acme/invoices");
    expect(screen.getByRole("link", { name: "All" })).toHaveAttribute("class", ACTIVE);
    expect(screen.getByRole("link", { name: "Draft" })).toHaveAttribute(
      "href",
      "/org/acme/invoices?status=DRAFT"
    );
  });
});
