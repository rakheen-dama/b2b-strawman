import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";

import { MilestoneEditor } from "@/components/proposals/milestone-editor";
import type { MilestoneData } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

afterEach(() => cleanup());

describe("MilestoneEditor", () => {
  it("add milestone increments rows", () => {
    const milestones: MilestoneData[] = [];
    let captured: MilestoneData[] = [];
    const onChange = vi.fn((m: MilestoneData[]) => {
      captured = m;
    });

    const { rerender } = render(
      <MilestoneEditor milestones={milestones} onChange={onChange} />,
    );

    fireEvent.click(screen.getByText("Add milestone"));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(captured).toHaveLength(1);

    // Rerender with updated milestones
    rerender(<MilestoneEditor milestones={captured} onChange={onChange} />);
    expect(screen.getAllByTestId("milestone-row")).toHaveLength(1);

    // Add another
    fireEvent.click(screen.getByText("Add milestone"));
    expect(captured).toHaveLength(2);
  });

  it("percentage total indicator turns green at 100", () => {
    const milestones: MilestoneData[] = [
      { description: "Phase 1", percentage: 60, relativeDueDays: 30 },
      { description: "Phase 2", percentage: 40, relativeDueDays: 60 },
    ];

    render(
      <MilestoneEditor milestones={milestones} onChange={vi.fn()} />,
    );

    const indicator = screen.getByTestId("milestone-total");
    expect(indicator.textContent).toBe("100% / 100%");
    expect(indicator.className).toContain("text-emerald-600");
  });

  it("remove milestone updates total", () => {
    const milestones: MilestoneData[] = [
      { description: "Phase 1", percentage: 60, relativeDueDays: 30 },
      { description: "Phase 2", percentage: 40, relativeDueDays: 60 },
    ];
    let captured: MilestoneData[] = milestones;
    const onChange = vi.fn((m: MilestoneData[]) => {
      captured = m;
    });

    const { rerender } = render(
      <MilestoneEditor milestones={milestones} onChange={onChange} />,
    );

    // Remove first milestone
    const removeButtons = screen.getAllByLabelText("Remove milestone");
    fireEvent.click(removeButtons[0]);

    expect(onChange).toHaveBeenCalled();
    expect(captured).toHaveLength(1);
    expect(captured[0].description).toBe("Phase 2");

    // Rerender to verify updated total
    rerender(<MilestoneEditor milestones={captured} onChange={onChange} />);
    const indicator = screen.getByTestId("milestone-total");
    expect(indicator.textContent).toBe("40% / 100%");
    expect(indicator.className).toContain("text-red-600");
  });
});
