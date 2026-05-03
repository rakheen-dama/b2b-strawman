import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { SeverityPill } from "../severity-pill";

afterEach(() => {
  cleanup();
});

describe("<SeverityPill>", () => {
  it("renders INFO with grey slate classes", () => {
    render(<SeverityPill severity="INFO" />);
    const pill = screen.getByTestId("severity-pill");
    expect(pill).toHaveTextContent("INFO");
    expect(pill.className).toContain("bg-slate-100");
    expect(pill.className).toContain("text-slate-700");
  });

  it("renders NOTICE with blue classes", () => {
    render(<SeverityPill severity="NOTICE" />);
    const pill = screen.getByTestId("severity-pill");
    expect(pill).toHaveTextContent("NOTICE");
    expect(pill.className).toContain("bg-blue-100");
    expect(pill.className).toContain("text-blue-700");
  });

  it("renders WARNING with amber classes", () => {
    render(<SeverityPill severity="WARNING" />);
    const pill = screen.getByTestId("severity-pill");
    expect(pill).toHaveTextContent("WARNING");
    expect(pill.className).toContain("bg-amber-100");
    expect(pill.className).toContain("text-amber-800");
  });

  it("renders CRITICAL with red classes", () => {
    render(<SeverityPill severity="CRITICAL" />);
    const pill = screen.getByTestId("severity-pill");
    expect(pill).toHaveTextContent("CRITICAL");
    expect(pill.className).toContain("bg-red-100");
    expect(pill.className).toContain("text-red-700");
  });

  it("respects size prop", () => {
    render(<SeverityPill severity="INFO" size="sm" />);
    const pill = screen.getByTestId("severity-pill");
    expect(pill.className).toContain("text-[10px]");
  });
});
