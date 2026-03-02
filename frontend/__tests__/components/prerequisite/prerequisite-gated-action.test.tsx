import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "@/components/ui/button";

const mockCheckPrerequisitesAction = vi.fn();

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: (...args: unknown[]) =>
    mockCheckPrerequisitesAction(...args),
  updateEntityCustomFieldsAction: vi.fn(),
}));

import { PrerequisiteGatedAction } from "@/components/prerequisite/prerequisite-gated-action";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("PrerequisiteGatedAction", () => {
  it("calls onAction when check passes", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();

    mockCheckPrerequisitesAction.mockResolvedValueOnce({
      passed: true,
      context: "DOCUMENT_GENERATION",
      violations: [],
    });

    render(
      <PrerequisiteGatedAction
        context="DOCUMENT_GENERATION"
        entityType="CUSTOMER"
        entityId="c1"
        slug="acme"
        onAction={onAction}
      >
        <Button>Generate Document</Button>
      </PrerequisiteGatedAction>,
    );

    await user.click(screen.getByRole("button", { name: "Generate Document" }));

    await waitFor(() => {
      expect(onAction).toHaveBeenCalled();
    });
    expect(
      screen.queryByText("Prerequisites: Document Generation"),
    ).not.toBeInTheDocument();
  });

  it("calls onAction after modal resolves", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();

    mockCheckPrerequisitesAction
      .mockResolvedValueOnce({
        passed: false,
        context: "DOCUMENT_GENERATION",
        violations: [
          {
            code: "MISSING_FIELD",
            message: "Country is required",
            entityType: "CUSTOMER",
            entityId: "c1",
            fieldSlug: null,
            groupName: null,
            resolution: "Add country to customer",
          },
        ],
      })
      // Second call: re-check after user fixes
      .mockResolvedValueOnce({
        passed: true,
        context: "DOCUMENT_GENERATION",
        violations: [],
      });

    render(
      <PrerequisiteGatedAction
        context="DOCUMENT_GENERATION"
        entityType="CUSTOMER"
        entityId="c1"
        slug="acme"
        onAction={onAction}
      >
        <Button>Generate Document</Button>
      </PrerequisiteGatedAction>,
    );

    // Click trigger — prereq check fails
    await user.click(screen.getByRole("button", { name: "Generate Document" }));

    // Modal appears
    await waitFor(() => {
      expect(
        screen.getByText("Prerequisites: Document Generation"),
      ).toBeInTheDocument();
    });

    // Click "Check & Continue" in modal
    await user.click(
      screen.getByRole("button", { name: /check & continue/i }),
    );

    await waitFor(() => {
      expect(onAction).toHaveBeenCalled();
    });
  });

  it("opens modal when check fails", async () => {
    const user = userEvent.setup();
    mockCheckPrerequisitesAction.mockResolvedValueOnce({
      passed: false,
      context: "DOCUMENT_GENERATION",
      violations: [
        {
          code: "MISSING_FIELD",
          message: "Country is required",
          entityType: "CUSTOMER",
          entityId: "c1",
          fieldSlug: null,
          groupName: null,
          resolution: "Add country to customer",
        },
      ],
    });

    render(
      <PrerequisiteGatedAction
        context="DOCUMENT_GENERATION"
        entityType="CUSTOMER"
        entityId="c1"
        slug="acme"
        onAction={vi.fn()}
      >
        <Button>Generate Document</Button>
      </PrerequisiteGatedAction>,
    );

    await user.click(screen.getByRole("button", { name: "Generate Document" }));

    await waitFor(() => {
      expect(
        screen.getByText("Prerequisites: Document Generation"),
      ).toBeInTheDocument();
      expect(screen.getByText("Country is required")).toBeInTheDocument();
    });
  });
});
