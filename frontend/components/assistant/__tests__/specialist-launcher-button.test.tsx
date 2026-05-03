import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CapabilityProvider } from "@/lib/capabilities";

// Mock the API module BEFORE importing the component-under-test
vi.mock("@/lib/api/assistant-specialists", () => ({
  startSession: vi.fn(),
}));

// Mock the SpecialistPanel — full Radix sheet rendering is unneeded here
vi.mock("@/components/assistant/specialist-panel", () => ({
  SpecialistPanel: ({ open }: { open: boolean }) =>
    open ? <div data-testid="specialist-panel-mock">Panel open</div> : null,
}));

import { SpecialistLauncherButton } from "@/components/assistant/specialist-launcher-button";
import { startSession } from "@/lib/api/assistant-specialists";

const startSessionMock = startSession as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("SpecialistLauncherButton", () => {
  it("is hidden when caller lacks AI_ASSISTANT_USE capability", () => {
    render(
      <CapabilityProvider capabilities={[]} role="member" isAdmin={false} isOwner={false}>
        <SpecialistLauncherButton
          specialistId="invoice-drafter"
          surface="INVOICE_DRAFT_TOOLBAR"
          contextRef={{ entityType: "INVOICE", entityId: "i-1" }}
          ctaLabel="Draft invoice with AI"
        />
      </CapabilityProvider>
    );

    expect(
      screen.queryByRole("button", { name: /draft invoice with ai/i })
    ).not.toBeInTheDocument();
  });

  it("renders for admins and starts a session on click", async () => {
    const user = userEvent.setup();
    startSessionMock.mockResolvedValueOnce({
      sessionId: "11111111-1111-1111-1111-111111111111",
      specialistId: "invoice-drafter",
      systemPromptHash: "deadbeef",
      toolIds: ["draft_invoice"],
      displayName: "Invoice Drafter",
      preSeededAssistantMessage: null,
    });

    render(
      <CapabilityProvider capabilities={[]} role="admin" isAdmin={true} isOwner={false}>
        <SpecialistLauncherButton
          specialistId="invoice-drafter"
          surface="INVOICE_DRAFT_TOOLBAR"
          contextRef={{ entityType: "INVOICE", entityId: "i-1" }}
          initialPrompt="Draft for me"
          ctaLabel="Draft invoice with AI"
        />
      </CapabilityProvider>
    );

    const trigger = screen.getByRole("button", { name: /draft invoice with ai/i });
    await user.click(trigger);

    await waitFor(() => expect(startSessionMock).toHaveBeenCalledTimes(1));
    expect(startSessionMock).toHaveBeenCalledWith("invoice-drafter", {
      contextRef: expect.objectContaining({
        entityType: "INVOICE",
        entityId: "i-1",
      }),
      initialPrompt: "Draft for me",
      surface: "INVOICE_DRAFT_TOOLBAR",
    });

    await waitFor(() =>
      expect(screen.getByTestId("specialist-panel-mock")).toBeInTheDocument()
    );
  });

  it("surfaces an error message when startSession rejects", async () => {
    const user = userEvent.setup();
    startSessionMock.mockRejectedValueOnce(new Error("boom"));

    render(
      <CapabilityProvider capabilities={[]} role="owner" isAdmin={false} isOwner={true}>
        <SpecialistLauncherButton
          specialistId="invoice-drafter"
          surface="INVOICE_DRAFT_TOOLBAR"
          contextRef={{ entityType: "INVOICE", entityId: "i-1" }}
          ctaLabel="Draft invoice with AI"
        />
      </CapabilityProvider>
    );

    const trigger = screen.getByRole("button", { name: /draft invoice with ai/i });
    await user.click(trigger);

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        /could not start specialist session/i
      )
    );
  });
});
