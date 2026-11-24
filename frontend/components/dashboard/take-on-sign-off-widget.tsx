"use client";

import { useState } from "react";
import { FileSignature } from "lucide-react";
import { getSignOffQueue } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { SignOffSheet } from "@/components/take-on/sign-off-sheet";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { takeOnLongDate, type TakeOnSignOffQueueItem } from "@/lib/types";
import { WIDGET_ROW_CLASS, WidgetCard, WidgetErrorLine } from "@/components/dashboard/widget-parts";

interface TakeOnSignOffWidgetProps {
  /** The sign-off queue (never empty here: an empty queue drops the widget); `null` = failed. */
  data: TakeOnSignOffQueueItem[] | null;
}

/**
 * "Trust take-on awaiting your sign-off" (Phase 93 657C, FD-36, R-10): the validated trust
 * take-ons a partner or director may sign, oldest first. Each opens the sign-off sheet; closing
 * the sheet after signing re-reads the queue, so a signed take-on leaves the list. Home renders it
 * only for a caller holding SIGN_OFF_TRUST_TAKE_ON and VIEW_TRUST, and only while the queue is
 * non-empty.
 */
export function TakeOnSignOffWidget({ data }: TakeOnSignOffWidgetProps) {
  const [items, setItems] = useState(data);
  const [refreshError, setRefreshError] = useState(false);

  async function refresh() {
    const result = await getSignOffQueue();
    if (result.success && result.data) {
      setItems(result.data);
      setRefreshError(false);
    } else {
      setRefreshError(true);
    }
  }

  return (
    <WidgetCard
      data-testid="take-on-sign-off-widget"
      title={
        <>
          <FileSignature aria-hidden="true" className="size-4 text-teal-600 dark:text-teal-400" />
          Trust take-on awaiting your sign-off
        </>
      }
      contentProps={{ className: "space-y-2" }}
    >
      {items === null || refreshError ? (
        <WidgetErrorLine subject="Trust take-ons awaiting sign-off" />
      ) : items.length === 0 ? (
        <p className="text-muted-foreground text-sm" data-testid="take-on-sign-off-none">
          Nothing else is awaiting your sign-off.
        </p>
      ) : (
        <ul className="space-y-0.5" aria-label="Trust take-ons awaiting your sign-off">
          {items.map((item) => (
            <li key={item.batchId}>
              <SignOffSheet
                batchId={item.batchId}
                trustAccountName={item.trustAccountName}
                cutoverDate={item.cutoverDate}
                onClosedAfterChange={refresh}
                triggerTestId={`take-on-sign-off-item-${item.batchId}`}
                triggerClassName={cn(WIDGET_ROW_CLASS, "w-full text-left text-sm")}
                triggerLabel={
                  <>
                    <span className="text-foreground min-w-0 flex-1 truncate">
                      {item.trustAccountName ?? "Trust account"} at{" "}
                      {takeOnLongDate(item.cutoverDate)}
                    </span>
                    <span className="text-muted-foreground shrink-0 text-xs">
                      {[
                        item.preparedByName && `Prepared by ${item.preparedByName}`,
                        item.validatedAt && `validated ${formatDateTime(item.validatedAt)}`,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </span>
                  </>
                }
              />
            </li>
          ))}
        </ul>
      )}
    </WidgetCard>
  );
}
