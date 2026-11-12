import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { HomeDiaryWidget } from "@/components/dashboard/home-diary-widget";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

// Phase 87 614B — Home's "Today's diary" card: the My work diary list in a Home card.

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const TODAY = "2026-10-21";

function makeEvent(o: Partial<DiaryEventResponse> & { id: string }): DiaryEventResponse {
  return {
    kind: "TASK_DUE",
    date: TODAY,
    title: `Event ${o.id}`,
    status: "OPEN",
    matterId: "m1",
    matterName: "Ndlovu v RAF",
    overdue: false,
    href: `/matters/m1?taskId=${o.id}`,
    ...o,
  };
}

describe("HomeDiaryWidget", () => {
  afterEach(() => cleanup());

  it("renders a card titled Today's diary with an Open diary link to the Day view", () => {
    render(<HomeDiaryWidget slug="acme" today={TODAY} diaryEvents={[]} />);

    const card = screen.getByTestId("home-diary-widget");
    expect(within(card).getByText("Today's diary")).toBeInTheDocument();
    expect(within(card).getByRole("link", { name: "Open diary" })).toHaveAttribute(
      "href",
      "/org/acme/diary?view=day"
    );
    // The list sits in the card's content section (the testid the agenda tests use).
    expect(
      within(within(card).getByTestId("todays-diary")).getByText("Nothing in the diary today")
    ).toBeInTheDocument();
  });

  it("lists today's rows and links the overdue band", () => {
    render(
      <HomeDiaryWidget
        slug="acme"
        today={TODAY}
        diaryEvents={[
          makeEvent({ id: "t1", title: "File plea" }),
          makeEvent({ id: "o1", date: "2026-10-20", overdue: true }),
        ]}
      />
    );

    expect(screen.getByText("File plea")).toBeInTheDocument();
    expect(screen.getByTestId("todays-diary-overdue")).toHaveTextContent("1 overdue");
  });

  it("goes two-column at @4xl only with ≥ 4 visible rows (640A)", () => {
    const events = (n: number) =>
      Array.from({ length: n }, (_, i) => makeEvent({ id: `t${i}`, title: `Row ${i}` }));
    const list = () => within(screen.getByTestId("todays-diary")).getByRole("list");

    for (const n of [1, 2, 3]) {
      render(<HomeDiaryWidget slug="acme" today={TODAY} diaryEvents={events(n)} />);
      expect(list()).toHaveAttribute("data-columns", "1");
      expect(list()).not.toHaveClass("@4xl:columns-2");
      cleanup();
    }
    for (const n of [4, 5, 7]) {
      render(<HomeDiaryWidget slug="acme" today={TODAY} diaryEvents={events(n)} />);
      expect(list()).toHaveAttribute("data-columns", "2");
      expect(list()).toHaveClass("@4xl:columns-2");
      expect(within(list()).getAllByRole("listitem")).toHaveLength(Math.min(n, 5));
      cleanup();
    }
  });

  it("an unidentified member gets the distinct message, never the firm's diary", () => {
    render(<HomeDiaryWidget slug="acme" today={TODAY} diaryEvents="unidentified" />);

    expect(screen.getByText(/Couldn't identify your member profile/)).toBeInTheDocument();
  });
});
