import Link from "next/link";
import {
  TodaysDiaryList,
  todaysDiaryHref,
  type TodaysDiaryEvents,
} from "@/components/dashboard/todays-agenda";
import { WIDGET_LINK_CLASS, WidgetCard } from "@/components/dashboard/widget-parts";

interface HomeDiaryWidgetProps {
  slug: string;
  /** Server "today" (yyyy-MM-dd) — the date the diary was fetched for. */
  today: string;
  diaryEvents: TodaysDiaryEvents;
}

/**
 * Today's diary on Home (614B, arch §12.6.3): the caller's own day for members, the firm's for
 * admins and owners — the same list as My work's agenda, in a Home card.
 */
export function HomeDiaryWidget({ slug, today, diaryEvents }: HomeDiaryWidgetProps) {
  return (
    <WidgetCard
      data-testid="home-diary-widget"
      title="Today's diary"
      action={
        <Link href={todaysDiaryHref(slug)} className={WIDGET_LINK_CLASS}>
          Open diary
        </Link>
      }
      // Full-width row on firm/owner Home (640A): the list goes two-column once the card is @4xl
      // (56rem) wide and holds ≥ 4 rows, so it doesn't stretch; fewer rows stay one column.
      contentProps={{ "data-testid": "todays-diary", className: "@container min-w-0" }}
    >
      <TodaysDiaryList slug={slug} today={today} diaryEvents={diaryEvents} twoColumnsWhenWide />
    </WidgetCard>
  );
}
