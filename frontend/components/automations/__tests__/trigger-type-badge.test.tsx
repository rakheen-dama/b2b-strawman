import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";

afterEach(() => {
  cleanup();
});

describe("TriggerTypeBadge", () => {
  // D4 / 625F (G-B13): the DOCUMENT_ACCEPTED trigger fires on upload, so it is labelled
  // "Document uploaded". The enum name is unchanged (stored in automation_rules.trigger_type).
  it('labels DOCUMENT_ACCEPTED as "Document uploaded"', () => {
    render(<TriggerTypeBadge triggerType="DOCUMENT_ACCEPTED" />);
    expect(screen.getByText("Document uploaded")).toBeInTheDocument();
    expect(screen.queryByText("Document Accepted")).not.toBeInTheDocument();
  });
});
