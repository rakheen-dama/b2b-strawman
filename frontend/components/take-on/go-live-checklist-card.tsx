"use client";

import { useState } from "react";
import { CheckCircle2, Circle } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Label } from "@b2mash/ui/label";
import { isoDateInSA } from "@b2mash/shared/date";
import { Textarea } from "@/components/ui/textarea";
import { SettingsSection } from "@/components/settings/settings-section";
import {
  acknowledgeGoLiveItem,
  withdrawGoLiveItem,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { formatLocalDate } from "@/lib/format";
import { takeOnKebab, type TakeOnGoLiveItem, type TakeOnGoLiveItemKey } from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";

const NOTE_MAX = 1000;

/** The South African calendar day of an acknowledgement ("24 Nov 2026"), the same on SSR and CSR. */
function acknowledgedOn(at: string): string {
  const d = new Date(at);
  return Number.isNaN(d.getTime()) ? "" : formatLocalDate(isoDateInSA(d));
}

function acknowledgedLine(item: TakeOnGoLiveItem): string {
  const on = item.acknowledgedAt ? ` on ${acknowledgedOn(item.acknowledgedAt)}` : "";
  return item.acknowledgedByName
    ? `Acknowledged by ${item.acknowledgedByName}${on}`
    : `Acknowledged${on}`;
}

interface GoLiveChecklistCardProps {
  items: TakeOnGoLiveItem[];
}

/**
 * The firm's go-live checklist on the Take-on tab (Phase 93 656A, §17.3.14): the four statements
 * exactly as the backend gives them (X3 legal copy), each acknowledged with an optional note or
 * withdrawn. Advisory — it allows or refuses nothing. A refusal shows the backend's text verbatim.
 */
export function GoLiveChecklistCard({ items: initialItems }: GoLiveChecklistCardProps) {
  const [items, setItems] = useState(initialItems);
  const [busyKey, setBusyKey] = useState<TakeOnGoLiveItemKey | null>(null);
  const [error, setError] = useState<{ key: TakeOnGoLiveItemKey; message: string } | null>(null);

  async function change(
    key: TakeOnGoLiveItemKey,
    call: () => ReturnType<typeof withdrawGoLiveItem>,
    fallback: string
  ): Promise<boolean> {
    setError(null);
    setBusyKey(key);
    try {
      const result = await call();
      if (!result.success || !result.data) {
        setError({ key, message: result.error ?? fallback });
        return false;
      }
      setItems(result.data);
      return true;
    } finally {
      setBusyKey(null);
    }
  }

  return (
    <SettingsSection
      title="Go-live checklist"
      description={
        <>
          Confirm each point for your firm. These are your firm&apos;s records; they do not stop a
          take-on.
        </>
      }
      data-testid="take-on-go-live-card"
    >
      <ul className="divide-border divide-y">
        {items.map((item) => (
          <GoLiveItemRow
            key={item.itemKey}
            item={item}
            busy={busyKey === item.itemKey}
            disabled={busyKey !== null}
            error={error?.key === item.itemKey ? error.message : null}
            onAcknowledge={(note) =>
              change(
                item.itemKey,
                () => acknowledgeGoLiveItem(item.itemKey, note),
                "The item could not be acknowledged. Try again."
              )
            }
            onWithdraw={() =>
              change(
                item.itemKey,
                () => withdrawGoLiveItem(item.itemKey),
                "The acknowledgement could not be withdrawn. Try again."
              )
            }
          />
        ))}
      </ul>
    </SettingsSection>
  );
}

interface GoLiveItemRowProps {
  item: TakeOnGoLiveItem;
  busy: boolean;
  disabled: boolean;
  error: string | null;
  onAcknowledge: (note: string | null) => Promise<boolean>;
  onWithdraw: () => Promise<boolean>;
}

function GoLiveItemRow({
  item,
  busy,
  disabled,
  error,
  onAcknowledge,
  onWithdraw,
}: GoLiveItemRowProps) {
  const [note, setNote] = useState("");
  const kebab = takeOnKebab(item.itemKey);
  const noteId = `take-on-go-live-note-${kebab}`;

  async function acknowledge() {
    if (await onAcknowledge(note.trim() || null)) setNote("");
  }

  return (
    <li
      className="space-y-3 py-4 first:pt-0 last:pb-0"
      data-testid={`take-on-go-live-item-${kebab}`}
    >
      <div className="flex gap-3">
        {item.acknowledged ? (
          <CheckCircle2
            aria-hidden
            className="mt-0.5 size-4 shrink-0 text-teal-600 dark:text-teal-400"
          />
        ) : (
          <Circle aria-hidden className="mt-0.5 size-4 shrink-0 text-slate-400" />
        )}
        <p
          className="text-sm text-slate-800 dark:text-slate-200"
          data-testid={`take-on-go-live-text-${kebab}`}
        >
          {item.text}
        </p>
      </div>
      <div className="space-y-2 pl-7">
        {item.acknowledged ? (
          <>
            <div
              className="space-y-1 text-sm text-slate-600 dark:text-slate-300"
              data-testid={`take-on-go-live-acknowledged-${kebab}`}
            >
              <p>{acknowledgedLine(item)}</p>
              {item.note && (
                <p className="whitespace-pre-line text-slate-700 dark:text-slate-300">
                  {item.note}
                </p>
              )}
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={disabled}
              onClick={onWithdraw}
              data-testid={`take-on-go-live-withdraw-${kebab}`}
            >
              {busy ? "Withdrawing…" : "Withdraw"}
            </Button>
          </>
        ) : (
          <>
            <Label htmlFor={noteId} className="text-xs text-slate-600 dark:text-slate-300">
              Note (optional)
            </Label>
            <Textarea
              id={noteId}
              value={note}
              maxLength={NOTE_MAX}
              rows={2}
              disabled={disabled}
              onChange={(e) => setNote(e.target.value)}
              data-testid={noteId}
            />
            <Button
              type="button"
              variant="soft"
              size="sm"
              disabled={disabled}
              onClick={acknowledge}
              data-testid={`take-on-go-live-acknowledge-${kebab}`}
            >
              {busy ? "Acknowledging…" : "Acknowledge"}
            </Button>
          </>
        )}
        {error && <TakeOnError message={error} testId="take-on-go-live-error" />}
      </div>
    </li>
  );
}
