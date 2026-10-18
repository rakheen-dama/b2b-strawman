import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { useEffect, useState } from "react";
import OrgTemplate from "@/app/(app)/org/[slug]/template";

// The template must not key anything on the pathname itself (remounting per section is
// Next's job via the template's segment key). The pathname changes between renders below so
// that a pathname-keyed wrapper — the old <PageTransition> — would fail the remount test.
let pathname = "/org/acme/legal/disbursements";
vi.mock("next/navigation", () => ({ usePathname: () => pathname }));

afterEach(() => cleanup());

let mounts = 0;

/** Stand-in for a page with local state (e.g. an open dialog) that must survive renders. */
function StatefulPage({ label }: { label: string }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    mounts += 1;
  }, []);
  return (
    <div data-testid="page">
      <span>{label}</span>
      <button onClick={() => setOpen(true)}>Approve</button>
      {open && <div role="dialog">Approve dialog</div>}
    </div>
  );
}

describe("org template (page entrance)", () => {
  it("renders the page inside a single CSS entrance wrapper that honours reduced motion", () => {
    const { container } = render(
      <OrgTemplate>
        <StatefulPage label="Disbursement" />
      </OrgTemplate>
    );

    const wrapper = container.firstElementChild as HTMLElement;
    expect(container.childElementCount).toBe(1);
    expect(wrapper.firstElementChild).toBe(screen.getByTestId("page"));
    expect(wrapper.className).toContain("animate-in");
    expect(wrapper.className).toContain("fade-in");
    expect(wrapper.className).toContain("motion-reduce:animate-none");
    // No JS-driven inline styles: server-rendered content is not held at opacity 0
    expect(wrapper.getAttribute("style")).toBeNull();
  });

  it("does not remount the page when the pathname changes — state opened by the user is kept", () => {
    mounts = 0;
    pathname = "/org/acme/legal/disbursements";
    const { rerender } = render(
      <OrgTemplate>
        <StatefulPage label="Disbursement" />
      </OrgTemplate>
    );
    const page = screen.getByTestId("page");
    fireEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    // The router commits the new pathname and re-renders with fresh children. The
    // wrapper must reuse the mounted page, not tear it down and mount it again (the old
    // pathname-keyed AnimatePresence remounted it here, discarding the open dialog).
    // Remounting on a section change is Next's job (the template's segment key).
    pathname = "/org/acme/legal/disbursements/0b6b1f8e-2f53-4a59-9d55-0e7f3c2f1a10";
    rerender(
      <OrgTemplate>
        <StatefulPage label="Disbursement (refreshed)" />
      </OrgTemplate>
    );

    expect(screen.getByTestId("page")).toBe(page);
    expect(screen.getByText("Disbursement (refreshed)")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(mounts).toBe(1);
  });
});
