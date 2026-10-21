import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditViewDialog } from "@/components/views/EditViewDialog";
import type { SavedViewResponse } from "@/lib/types";

function savedView(overrides: Partial<SavedViewResponse> = {}): SavedViewResponse {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    entityType: "CUSTOMER",
    name: "Old clients view",
    filters: {},
    columns: null,
    shared: false,
    createdBy: "m1",
    sortOrder: 0,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

const onSave = vi.fn();

function renderDialog(view: SavedViewResponse) {
  render(
    <EditViewDialog
      slug="acme"
      view={view}
      allTags={[]}
      fieldDefinitions={[]}
      canEdit={true}
      onSave={onSave}
    >
      <button type="button">Edit view</button>
    </EditViewDialog>
  );
}

async function openColumnsStep() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Edit view" }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  return user;
}

beforeEach(() => {
  vi.clearAllMocks();
  onSave.mockResolvedValue({ success: true });
});
afterEach(() => cleanup());

describe("EditViewDialog — pre-615D CUSTOMER column ids", () => {
  it("shows legacy email/phone as the Contact column and drops status", async () => {
    // A view saved before 615D with the old CUSTOMER column ids.
    renderDialog(savedView({ columns: ["name", "email", "phone", "status", "createdAt"] }));
    await openColumnsStep();

    expect(screen.getByRole("checkbox", { name: "Client" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Contact" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Created" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Lifecycle" })).not.toBeChecked();
  });

  it("saves current ids only, so unchecking Contact removes it", async () => {
    renderDialog(savedView({ columns: ["name", "email", "phone", "status", "createdAt"] }));
    const user = await openColumnsStep();

    await user.click(screen.getByRole("checkbox", { name: "Contact" }));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(onSave).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      expect.objectContaining({ columns: ["name", "createdAt"] })
    );
  });

  it("leaves non-CUSTOMER column ids untouched", async () => {
    renderDialog(savedView({ entityType: "TASK", columns: ["title", "status"] }));
    await openColumnsStep();

    expect(screen.getByRole("checkbox", { name: "Title" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Status" })).toBeChecked();
  });
});
