import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardAction, CardContent, CardFooter, CardHeader, CardTitle } from "@b2mash/ui/card";
import type { FicaOutstandingResponse, FicaOutstandingStatus } from "@/lib/dashboard-types";

const LINK_CLASS = "text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100";

const STATUS_LABEL: Record<FicaOutstandingStatus, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
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
    <Card data-testid="fica-outstanding-widget" className="gap-4">
      <CardHeader>
        <CardTitle className="text-sm font-medium">FICA outstanding</CardTitle>
        <CardAction>
          {/* TODO(615C): /clients?view=system:fica-outstanding once CLIENT_SYSTEM_VIEWS land */}
          <Link href={`/org/${slug}/compliance`} className={LINK_CLASS}>
            Open compliance
          </Link>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        {data === null ? (
          <p className="text-muted-foreground text-sm italic">
            FICA status could not be loaded. Refresh to try again.
          </p>
        ) : data.outstandingCount === 0 ? (
          <p className="text-sm text-slate-400">Every active client has completed FICA.</p>
        ) : (
          <>
            <div>
              <p
                data-testid="fica-outstanding-headline"
                className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50"
              >
                {data.outstandingCount}
              </p>
              <p className="text-xs text-slate-500">
                of {data.totalActiveClients} active{" "}
                {data.totalActiveClients === 1 ? "client" : "clients"} without completed FICA
              </p>
            </div>
            <ul className="space-y-0.5" aria-label="Clients with FICA outstanding">
              {data.clients.map((client) => (
                <li key={client.clientId}>
                  <Link
                    href={`/org/${slug}/clients/${client.clientId}`}
                    className="focus-visible:ring-ring flex items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none hover:bg-slate-50 focus-visible:ring-2 dark:hover:bg-slate-900"
                  >
                    <span className="min-w-0 flex-1 truncate">{client.clientName}</span>
                    <Badge variant="neutral">{STATUS_LABEL[client.ficaStatus]}</Badge>
                    <span className="w-16 text-right font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                      {client.daysOutstanding} {client.daysOutstanding === 1 ? "day" : "days"}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </CardContent>
      <CardFooter>
        <Link href={`/org/${slug}/clients?showIncomplete=true`} className={LINK_CLASS}>
          Incomplete profiles
        </Link>
      </CardFooter>
    </Card>
  );
}
