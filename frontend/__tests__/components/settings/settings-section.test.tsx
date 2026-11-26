import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SettingsActionBar, SettingsSection } from "@/components/settings/settings-section";

// Phase 91 664A (664.2): layout-only settings primitives. Assert structure and state (roles,
// headings, data-* attributes, slot order), never colour classes.

afterEach(() => {
  cleanup();
});

function section(testId = "sec") {
  return screen.getByTestId(testId);
}

describe("SettingsSection", () => {
  it("renders the title as an h2 by default", () => {
    render(
      <SettingsSection title="Section alpha" data-testid="sec">
        body
      </SettingsSection>
    );
    expect(screen.getByRole("heading", { level: 2, name: "Section alpha" })).toBeInTheDocument();
    expect(section()).toHaveTextContent("body");
  });

  it("renders the title as an h3 when asked", () => {
    render(
      <SettingsSection title="Section beta" as="h3">
        body
      </SettingsSection>
    );
    expect(screen.getByRole("heading", { level: 3, name: "Section beta" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 2 })).not.toBeInTheDocument();
  });

  it("renders no heading (and no header) without a title", () => {
    render(<SettingsSection data-testid="sec">only content</SettingsSection>);
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(section().querySelector('[data-slot="card-header"]')).toBeNull();
    expect(section()).toHaveTextContent("only content");
  });

  it("renders the description under the title", () => {
    render(
      <SettingsSection title="Section gamma" description="Gamma description" data-testid="sec">
        body
      </SettingsSection>
    );
    const header = section().querySelector('[data-slot="card-header"]')!;
    expect(header).toHaveTextContent("Gamma description");
    expect(header.querySelector('[data-slot="card-description"]')).toHaveTextContent(
      "Gamma description"
    );
  });

  it("puts the actions in the header's action slot", () => {
    render(
      <SettingsSection
        title="Section delta"
        actions={<button type="button">Delta action</button>}
        data-testid="sec"
      >
        body
      </SettingsSection>
    );
    const action = section().querySelector('[data-slot="card-header"] [data-slot="card-action"]');
    expect(action).not.toBeNull();
    expect(action).toContainElement(screen.getByRole("button", { name: "Delta action" }));
  });

  it("renders the footer only when a footer or a status is given", () => {
    render(
      <SettingsSection
        title="Section epsilon"
        footer={<button type="button">Epsilon save</button>}
        data-testid="sec"
      >
        body
      </SettingsSection>
    );
    const footer = section().querySelector('[data-slot="card-footer"]');
    expect(footer).not.toBeNull();
    expect(footer).toContainElement(screen.getByRole("button", { name: "Epsilon save" }));
    cleanup();

    render(
      <SettingsSection title="Section zeta" data-testid="sec">
        body
      </SettingsSection>
    );
    expect(section().querySelector('[data-slot="card-footer"]')).toBeNull();
  });

  it("puts the status on the left of the footer, before the footer actions", () => {
    render(
      <SettingsSection
        title="Section eta"
        status={<span>Eta status</span>}
        footer={<button type="button">Eta save</button>}
        data-testid="sec"
      >
        body
      </SettingsSection>
    );
    const footer = section().querySelector('[data-slot="card-footer"]')!;
    const [statusSlot, actionsSlot] = Array.from(footer.children);
    expect(statusSlot).toHaveTextContent("Eta status");
    expect(actionsSlot).toContainElement(screen.getByRole("button", { name: "Eta save" }));
    cleanup();

    // A status alone still renders the footer row.
    render(
      <SettingsSection title="Section theta" status="Theta status" data-testid="sec">
        body
      </SettingsSection>
    );
    expect(section().querySelector('[data-slot="card-footer"]')).toHaveTextContent("Theta status");
  });

  it("marks the variant with data-variant and adds no heading text for destructive", () => {
    render(
      <SettingsSection title="Section iota" variant="destructive" data-testid="sec">
        body
      </SettingsSection>
    );
    expect(section()).toHaveAttribute("data-variant", "destructive");
    expect(section()).toHaveAttribute("data-slot", "settings-section");
    expect(screen.getAllByRole("heading").map((h) => h.textContent)).toEqual(["Section iota"]);
    cleanup();

    render(<SettingsSection data-testid="sec">body</SettingsSection>);
    expect(section()).toHaveAttribute("data-variant", "default");
  });

  it("passes data-testid, id and className through to the root", () => {
    render(
      <SettingsSection title="Section kappa" id="kappa" className="kappa-extra" data-testid="kappa">
        body
      </SettingsSection>
    );
    const root = screen.getByTestId("kappa");
    expect(root).toHaveAttribute("id", "kappa");
    expect(root).toHaveClass("kappa-extra");
  });
});

describe("SettingsActionBar", () => {
  it("renders the actions with the status on the left, not sticky by default", () => {
    const { container } = render(
      <SettingsActionBar status={<span>Bar status</span>}>
        <button type="button">Bar save</button>
      </SettingsActionBar>
    );
    const bar = container.querySelector('[data-slot="settings-action-bar"]')!;
    expect(bar).not.toHaveAttribute("data-sticky");
    expect(bar.firstElementChild).toHaveTextContent("Bar status");
    expect(bar).toContainElement(screen.getByRole("button", { name: "Bar save" }));
  });

  it("marks a sticky bar with data-sticky", () => {
    const { container } = render(
      <SettingsActionBar sticky>
        <button type="button">Sticky save</button>
      </SettingsActionBar>
    );
    expect(container.querySelector('[data-slot="settings-action-bar"]')).toHaveAttribute(
      "data-sticky",
      "true"
    );
  });
});
