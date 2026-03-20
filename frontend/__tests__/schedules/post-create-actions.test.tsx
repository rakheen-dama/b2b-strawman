import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PostCreateActionsSection } from "@/components/schedules/PostCreateActionsSection";
import type {
  DocumentTemplateOption,
  RequestTemplateOption,
  PostCreateActions,
} from "@/components/schedules/PostCreateActionsSection";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const DOC_TEMPLATES: DocumentTemplateOption[] = [
  { slug: "engagement-letter", name: "Engagement Letter" },
];

const REQUEST_TEMPLATES: RequestTemplateOption[] = [
  { slug: "year-end-info", name: "Year-End Info Request" },
];

describe("PostCreateActionsSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders section heading and helper text", () => {
    const onChange = vi.fn();
    render(
      <PostCreateActionsSection
        documentTemplates={[]}
        requestTemplates={[]}
        value={null}
        onChange={onChange}
      />,
    );
    expect(screen.getByText("After Creation (Optional)")).toBeInTheDocument();
    expect(
      screen.getByText(/These actions run automatically/),
    ).toBeInTheDocument();
  });

  it("generate document toggle shows and hides template selector", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <PostCreateActionsSection
        documentTemplates={DOC_TEMPLATES}
        requestTemplates={[]}
        value={null}
        onChange={onChange}
      />,
    );

    // Initially: selector not visible
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();

    // Turn on the toggle
    const generateSwitch = screen.getByRole("switch", {
      name: /Generate document/i,
    });
    await user.click(generateSwitch);

    // Now selector should be visible
    expect(screen.getByRole("combobox")).toBeInTheDocument();

    // Turn it off again
    await user.click(generateSwitch);
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("onChange emits correct payload when both toggles are on", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <PostCreateActionsSection
        documentTemplates={DOC_TEMPLATES}
        requestTemplates={REQUEST_TEMPLATES}
        value={null}
        onChange={onChange}
      />,
    );

    // Enable generate document
    const generateSwitch = screen.getByRole("switch", {
      name: /Generate document/i,
    });
    await user.click(generateSwitch);

    // Enable send info request
    const infoRequestSwitch = screen.getByRole("switch", {
      name: /Send information request/i,
    });
    await user.click(infoRequestSwitch);

    // Check the last onChange call has both keys
    const lastCall = onChange.mock.calls[onChange.mock.calls.length - 1][0] as
      | PostCreateActions
      | null;
    expect(lastCall).not.toBeNull();
    expect(lastCall?.generateDocument).toBeDefined();
    expect(lastCall?.generateDocument?.templateSlug).toBe("engagement-letter");
    expect(lastCall?.generateDocument?.autoSend).toBe(false);
    expect(lastCall?.sendInfoRequest).toBeDefined();
    expect(lastCall?.sendInfoRequest?.requestTemplateSlug).toBe(
      "year-end-info",
    );
    expect(lastCall?.sendInfoRequest?.dueDays).toBe(14);
  });

  it("auto-send checkbox is always disabled", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <PostCreateActionsSection
        documentTemplates={DOC_TEMPLATES}
        requestTemplates={[]}
        value={null}
        onChange={onChange}
      />,
    );

    // Enable generate document to reveal auto-send
    const generateSwitch = screen.getByRole("switch", {
      name: /Generate document/i,
    });
    await user.click(generateSwitch);

    const autoSendCheckbox = screen.getByRole("checkbox", {
      name: /Auto-send/i,
    });
    expect(autoSendCheckbox).toBeDisabled();
  });
});
