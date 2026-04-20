import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { MatterSelector } from "@/components/trust/matter-selector";

describe("MatterSelector", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders an empty state when no matters are supplied", () => {
    render(<MatterSelector matters={[]} />);
    expect(
      screen.getByText("No trust activity on your matters"),
    ).toBeInTheDocument();
  });

  it("renders a card per matter and links to the detail route", () => {
    render(
      <MatterSelector
        matters={[
          {
            matterId: "ab12cd34-ef56-7890-abcd-ef0123456789",
            currentBalance: 1250,
            lastTransactionAt: new Date().toISOString(),
            lastSyncedAt: new Date().toISOString(),
          },
          {
            matterId: "99887766-5544-3322-1100-ffeeddccbbaa",
            currentBalance: 500,
            lastTransactionAt: new Date().toISOString(),
            lastSyncedAt: new Date().toISOString(),
          },
        ]}
      />,
    );

    expect(screen.getByText("Matter ab12cd34")).toBeInTheDocument();
    expect(screen.getByText("Matter 99887766")).toBeInTheDocument();

    const links = screen.getAllByRole("link");
    const hrefs = links.map((l) => l.getAttribute("href"));
    expect(hrefs).toContain("/trust/ab12cd34-ef56-7890-abcd-ef0123456789");
    expect(hrefs).toContain("/trust/99887766-5544-3322-1100-ffeeddccbbaa");
  });
});
