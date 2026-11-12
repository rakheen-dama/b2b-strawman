import type { ReactNode } from "react";
import Link from "next/link";
import { CircleDashed, CircleDot, ShieldCheck } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { cn } from "@/lib/utils";
import type { FicaOutstandingResponse, FicaOutstandingStatus } from "@/lib/dashboard-types";
import {
  StatusBadge,
  type StatusTone,
  WIDGET_LINK_CLASS,
  WIDGET_ROW_CLASS,
  WidgetCard,
  WidgetErrorLine,
  WidgetHeadline,
} from "@/components/dashboard/widget-parts";

const STATUS: Record<FicaOutstandingStatus, { label: string; tone: StatusTone; icon: ReactNode }> =
  {
    NOT_STARTED: { label: "Not started", tone: "warning", icon: <CircleDashed /> },
    IN_PROGRESS: { label: "In progress", tone: "info", icon: <CircleDot /> },
  };

interface FicaOutstandingWidgetProps {
  slug: string;
  /** `null` = the read failed. */
  data: FicaOutstandingResponse | null;
}

/**
 * FICA outstanding (614B, arch §12.6.3): onboarding and active clients whose FICA is not done,
 * longest outstanding first (the read returns the top 5). Footer carries the incomplete-profiles
 * link that used to live in the admin stats column.
 */
export function FicaOutstandingWidget({ slug, data }: FicaOutstandingWidgetProps) {
  return (
    <WidgetCard
      data-testid="fica-outstanding-widget"
      title="FICA outstanding"
      action={
        <>
          {/* 615D: the Clients "FICA outstanding" system view (all lifecycles, so its count can
              differ from this widget's onboarding + active headline). */}
          <Link
            href={`/org/${slug}/clients?view=system:fica-outstanding`}
            className={WIDGET_LINK_CLASS}
          >
            View all
          </Link>
          <Link href={`/org/${slug}/compliance`} className={WIDGET_LINK_CLASS}>
            Open compliance
          </Link>
        </>
      }
      footer={
        <Link href={`/org/${slug}/clients?showIncomplete=true`} className={WIDGET_LINK_CLASS}>
          Incomplete profiles
        </Link>
      }
      contentProps={{ className: "space-y-3" }}
    >
      {data === null ? (
        <WidgetErrorLine subject="FICA status" />
      ) : data.outstandingCount === 0 ? (
        <EmptyState
          variant="compact"
          icon={ShieldCheck}
          title="Every active client has completed FICA."
          description={null}
        />
      ) : (
        <>
          <div>
            <WidgetHeadline testId="fica-outstanding-headline">
              {data.outstandingCount}
            </WidgetHeadline>
            <p className="text-muted-foreground text-xs">
              of {data.totalActiveClients} active{" "}
              {data.totalActiveClients === 1 ? "client" : "clients"} without completed FICA
            </p>
          </div>
          <ul className="space-y-0.5" aria-label="Clients with FICA outstanding">
            {data.clients.map((client) => {
              const status = STATUS[client.ficaStatus];
              return (
                <li key={client.clientId}>
                  <Link
                    href={`/org/${slug}/clients/${client.clientId}`}
                    className={cn(WIDGET_ROW_CLASS, "text-sm")}
                  >
                    <span className="text-foreground min-w-0 flex-1 truncate">
                      {client.clientName}
                    </span>
                    <StatusBadge tone={status.tone} icon={status.icon}>
                      {status.label}
                    </StatusBadge>
                    <span className="text-muted-foreground w-16 text-right font-mono text-xs tabular-nums">
                      {client.daysOutstanding} {client.daysOutstanding === 1 ? "day" : "days"}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </WidgetCard>
  );
}
