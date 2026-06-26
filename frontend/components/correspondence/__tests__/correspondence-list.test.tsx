import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CorrespondenceList } from "@/components/correspondence/correspondence-list";
import type { CorrespondenceListItem } from "@/lib/api/correspondence";

vi.mock("server-only", () => ({}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...p
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...p}>
      {children}
    </a>
  ),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function makeItem(overrides: Partial<CorrespondenceListItem> = {}): CorrespondenceListItem {
  return {
    id: "corr-1",
    subject: "Settlement proposal",
    fromAddress: "jane@acme.co.za",
    receivedAt: "2026-06-15T13:29:00Z",
    attachmentCount: 2,
    direction: "INBOUND",
    ...overrides,
  };
}

describe("CorrespondenceList", () => {
  it("renders filed correspondence rows with subject, sender and attachment count", () => {
    render(<CorrespondenceList items={[makeItem()]} slug="acme" projectId="proj-1" />);

    expect(screen.getByText("Settlement proposal")).toBeInTheDocument();
    expect(screen.getByText("jane@acme.co.za")).toBeInTheDocument();
    // Attachment count is a link to the matter's Documents tab.
    const link = screen.getByRole("link", { name: /2/ });
    expect(link).toHaveAttribute("href", "/org/acme/projects/proj-1?tab=documents");
  });

  it("shows the empty state when there is no correspondence", () => {
    render(<CorrespondenceList items={[]} slug="acme" projectId="proj-1" />);

    expect(screen.getByText(/no correspondence yet/i)).toBeInTheDocument();
    // No table rows / attachment links.
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders a plain '0' (not a link) when there are no attachments", () => {
    render(
      <CorrespondenceList
        items={[makeItem({ attachmentCount: 0 })]}
        slug="acme"
        projectId="proj-1"
      />
    );
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("0")).toBeInTheDocument();
  });

  it("renders an attacker-influenced subject as text, never as raw HTML (stored-XSS guard)", () => {
    const malicious = '<img src=x onerror="alert(1)">';
    const { container } = render(
      <CorrespondenceList items={[makeItem({ subject: malicious })]} slug="acme" projectId="p" />
    );

    // The subject string appears verbatim as text (React escapes it) ...
    expect(screen.getByText(malicious)).toBeInTheDocument();
    // ... and no <img> element was injected into the DOM.
    expect(container.querySelector("img")).toBeNull();
  });
});
