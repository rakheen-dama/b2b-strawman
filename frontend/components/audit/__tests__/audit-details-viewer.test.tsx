import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { AuditDetailsViewer } from "../audit-details-viewer";

afterEach(() => {
  cleanup();
});

describe("<AuditDetailsViewer>", () => {
  it("renders empty state when details is null", () => {
    render(<AuditDetailsViewer details={null} />);
    expect(screen.getByTestId("audit-details-empty")).toBeInTheDocument();
  });

  it("renders empty state when details is empty object", () => {
    render(<AuditDetailsViewer details={{}} />);
    expect(screen.getByTestId("audit-details-empty")).toBeInTheDocument();
  });

  it("renders side-by-side diff for AuditDeltaBuilder shape ({field: {from, to}})", () => {
    const details = {
      name: { from: "Acme Old", to: "Acme New" },
      email: { from: "old@x.com", to: "new@x.com" },
    };
    render(<AuditDetailsViewer details={details} />);

    expect(screen.getByTestId("audit-details-diff")).toBeInTheDocument();
    expect(screen.queryByTestId("audit-details-tree")).not.toBeInTheDocument();
    const rows = screen.getAllByTestId("diff-row");
    expect(rows).toHaveLength(2);
    expect(screen.getByText("Acme Old")).toBeInTheDocument();
    expect(screen.getByText("Acme New")).toBeInTheDocument();
    expect(screen.getByText("old@x.com")).toBeInTheDocument();
    expect(screen.getByText("new@x.com")).toBeInTheDocument();
  });

  it("renders diff for {before, after, changedFields} shape", () => {
    const details = {
      before: { name: "Old", count: 1 },
      after: { name: "New", count: 2 },
      changedFields: ["name", "count"],
    };
    render(<AuditDetailsViewer details={details} />);
    expect(screen.getByTestId("audit-details-diff")).toBeInTheDocument();
    const rows = screen.getAllByTestId("diff-row");
    expect(rows).toHaveLength(2);
  });

  it("renders JSON tree for free-form details (not delta shape)", () => {
    const details = {
      justification: "Client returned funds",
      override: true,
      counts: { reviewed: 3 },
    };
    render(<AuditDetailsViewer details={details} />);

    expect(screen.getByTestId("audit-details-tree")).toBeInTheDocument();
    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();
    expect(screen.getByText("justification:")).toBeInTheDocument();
    expect(screen.getByText("Client returned funds")).toBeInTheDocument();
  });
});
