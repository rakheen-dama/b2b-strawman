import Link from "next/link";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import {
  TodaysDiaryList,
  todaysDiaryHref,
  type TodaysDiaryEvents,
} from "@/components/dashboard/todays-agenda";
import { WIDGET_LINK_CLASS } from "@/components/dashboard/widget-parts";

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
    <Card data-testid="home-diary-widget" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">Today&apos;s diary</CardTitle>
        <CardAction>
          <Link href={todaysDiaryHref(slug)} className={WIDGET_LINK_CLASS}>
            Open diary
          </Link>
        </CardAction>
      </CardHeader>
      {/* Full-width row on firm/owner Home (640A): two columns once the card is @4xl (56rem) wide,
          so the list doesn't stretch; reading order flows down column 1, then column 2. */}
      <CardContent
        data-testid="todays-diary"
        className="@container min-w-0 @4xl:[&>ul]:columns-2 @4xl:[&>ul]:gap-x-8 [&>ul>li]:break-inside-avoid"
      >
        <TodaysDiaryList slug={slug} today={today} diaryEvents={diaryEvents} />
      </CardContent>
    </Card>
  );
}
