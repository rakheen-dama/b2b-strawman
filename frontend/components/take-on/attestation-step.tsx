"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { attest, revokeSignOff } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { formatDateTime } from "@/lib/format";
import {
  TAKE_ON_CLIENT_VALUE_LABEL,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnSignOff,
} from "@/lib/types";
import { CommitTakeOnDialog } from "./commit-dialog";
import { TakeOnError } from "./take-on-feedback";

/** The FICA columns the backend summarises at validation (catalogue keys of the clients dataset). */
const FICA_FIELDS: ReadonlyArray<readonly [string, string]> = [
  ["ficaClientType", "Client type"],
  ["riskRating", "Risk rating"],
  ["beneficialOwnershipStatus", "Beneficial ownership"],
  ["screeningStatus", "Screening"],
  ["sourceOfFundsStatus", "Source of funds"],
];

/** The backend's TAKE_ON_NOTHING_TO_ATTEST text, shown before anyone tries. */
const NOTHING_TO_ATTEST =
  "This take-on batch has no valid rows to attest. Correct the file and validate it again.";
const NOT_PERMITTED =
  "Attesting needs permission to manage compliance and clients. Ask an owner or admin.";
const NO_STATEMENT = "The attestation statement could not be loaded. Reload the take-on.";
const NO_SUMMARY = "Validate the batch again to see the FICA columns summarised.";

/** The batch defaults the attestation repeats back: exactly the source's mapping defaults (D-89). */
export function batchDefaults(batch: TakeOnBatch): Record<string, string> {
  return batch.sources[0]?.mapping?.defaults ?? {};
}

export function liveAttestation(batch: TakeOnBatch): TakeOnSignOff | null {
  return batch.signOffs.find((s) => s.kind === "FICA_ATTESTATION") ?? null;
}

/**
 * One FICA column's counts as "Label n · Label n", in the catalogue's value order (else the label
 * map's, which follows it) — labels, never codes; "—" when nothing was counted.
 */
function ficaCounts(
  key: string,
  counts: Record<string, number> | undefined,
  allowedValues: string[] | null | undefined
): string {
  const order = allowedValues ?? Object.keys(TAKE_ON_CLIENT_VALUE_LABEL[key] ?? {});
  const rank = (value: string) => {
    const i = order.indexOf(value);
    return i < 0 ? order.length : i;
  };
  return (
    Object.entries(counts ?? {})
      .filter(([, n]) => n > 0)
      .sort(([a], [b]) => rank(a) - rank(b))
      .map(([value, n]) => `${TAKE_ON_CLIENT_VALUE_LABEL[key]?.[value] ?? value} ${n}`)
      .join(" · ") || "—"
  );
}

interface AttestationStepProps {
  batch: TakeOnBatch;
  dataset: TakeOnDatasetCatalogue;
  onBatchChange: (batch: TakeOnBatch) => void;
  /** The commit was accepted: the batch is COMMITTING. */
  onCommitted: (batch: TakeOnBatch) => void;
}

/**
 * The FICA attestation of a validated clients batch (Phase 93 655A, §17.3.3, FD-37): the
 * FICA-ATTEST-v1 statement exactly as the server words it, the batch defaults, a summary of the
 * FICA columns, an accept checkbox, then Attest. With a live attestation: who attested, Withdraw,
 * and the commit. Every control follows the batch's allowedActions.
 */
export function AttestationStep({
  batch,
  dataset,
  onBatchChange,
  onCommitted,
}: AttestationStepProps) {
  const allowed = new Set(batch.allowedActions);
  const live = liveAttestation(batch);
  const statement = batch.attestationStatement;
  const defaults = batchDefaults(batch);
  const validated = batch.status === "VALIDATED";

  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Counted by the backend at validation (take_on_batches.summary) — no rows reach the browser.
  const ficaSummary =
    batch.summary?.fica && typeof batch.summary.clients === "number"
      ? { clients: batch.summary.clients, byField: batch.summary.fica }
      : null;
  const catalogueFields = dataset.sections.flatMap((s) => s.fields);
  const fieldLabel = new Map(catalogueFields.map((f) => [f.key, f.label] as const));
  const fieldValues = new Map(catalogueFields.map((f) => [f.key, f.allowedValues] as const));

  async function submit(call: () => ReturnType<typeof attest>, fallback: string) {
    setError(null);
    setBusy(true);
    try {
      const result = await call();
      if (!result.success || !result.data) {
        setError(result.error ?? fallback);
        return;
      }
      setAccepted(false);
      onBatchChange(result.data);
    } finally {
      setBusy(false);
    }
  }

  function attestNow() {
    if (!statement || !batch.validationHash || !accepted) return;
    const request = {
      validationHash: batch.validationHash,
      statementVersion: statement.version,
      defaults,
    };
    return submit(() => attest(batch.id, request), "The batch could not be attested.");
  }

  function withdraw() {
    if (!live) return;
    return submit(
      () => revokeSignOff(batch.id, live.id),
      "The attestation could not be withdrawn."
    );
  }

  const defaultEntries = Object.entries(defaults);

  return (
    <div className="space-y-6" data-testid="take-on-attestation">
      <div className="space-y-1">
        <h3 className="text-lg font-medium text-slate-950 dark:text-slate-50">FICA attestation</h3>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Attest the FICA records of this batch before committing it.
        </p>
      </div>

      {validated && batch.lastError && !live && (
        <TakeOnError message={batch.lastError} testId="take-on-last-error" />
      )}

      {statement && (
        <figure className="space-y-2">
          <blockquote
            data-testid="take-on-attestation-statement"
            className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-relaxed text-slate-800 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
          >
            {statement.text}
          </blockquote>
          <figcaption className="text-xs text-slate-500 dark:text-slate-400">
            Statement <span className="font-mono">{statement.version}</span>
          </figcaption>
        </figure>
      )}

      <section className="space-y-2" data-testid="take-on-attestation-defaults">
        <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">Batch defaults</h4>
        {defaultEntries.length === 0 ? (
          <p className="text-sm text-slate-600 dark:text-slate-400">
            None. Every row gives its own CDD records location.
          </p>
        ) : (
          <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
            {defaultEntries.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-slate-500 dark:text-slate-400">{fieldLabel.get(key) ?? key}</dt>
                <dd
                  className="text-slate-900 dark:text-slate-100"
                  data-testid={`take-on-attestation-default-${key}`}
                >
                  {value}
                </dd>
              </div>
            ))}
          </dl>
        )}
        <p className="text-xs text-slate-500 dark:text-slate-400">
          A default fills only the rows that leave the column blank.
        </p>
      </section>

      {validated && (
        <section className="space-y-2" data-testid="take-on-attestation-fica-summary">
          <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">
            FICA columns
            {ficaSummary && (
              <span className="font-normal text-slate-500 dark:text-slate-400">
                {" "}
                of the <span className="font-mono tabular-nums">{ficaSummary.clients}</span>{" "}
                {ficaSummary.clients === 1 ? "client" : "clients"} to be created
              </span>
            )}
          </h4>
          {!ficaSummary && (
            <p
              className="text-sm text-slate-500 dark:text-slate-400"
              data-testid="take-on-attestation-fica-summary-missing"
            >
              {NO_SUMMARY}
            </p>
          )}
          {ficaSummary && (
            <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
              {FICA_FIELDS.map(([key, label]) => (
                <div key={key} className="contents">
                  <dt className="text-slate-500 dark:text-slate-400">
                    {fieldLabel.get(key) ?? label}
                  </dt>
                  <dd
                    className="text-slate-900 dark:text-slate-100"
                    data-testid={`take-on-fica-summary-${key}`}
                  >
                    {ficaCounts(key, ficaSummary.byField[key], fieldValues.get(key))}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </section>
      )}

      {live ? (
        <div
          className="space-y-3 rounded-lg border border-teal-200 bg-teal-50 px-4 py-3 dark:border-teal-900 dark:bg-teal-950"
          data-testid="take-on-attestation-live"
        >
          <p className="text-sm text-teal-900 dark:text-teal-100">
            Attested by {live.memberName} ({live.roleName}) on {formatDateTime(live.signedAt)}.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            {allowed.has("COMMIT") && (
              <CommitTakeOnDialog batch={batch} onCommitted={onCommitted} />
            )}
            {allowed.has("REVOKE_ATTESTATION") && (
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={withdraw}
                data-testid="take-on-attestation-withdraw"
              >
                {busy ? "Withdrawing…" : "Withdraw attestation"}
              </Button>
            )}
          </div>
        </div>
      ) : allowed.has("ATTEST") && statement ? (
        <div className="space-y-3">
          <label className="flex items-start gap-2 text-sm text-slate-800 dark:text-slate-200">
            <Checkbox
              checked={accepted}
              onCheckedChange={(c) => setAccepted(c === true)}
              disabled={busy}
              className="mt-0.5"
              data-testid="take-on-attestation-accept"
            />
            I have read the statement above and I attest to it.
          </label>
          <Button
            type="button"
            variant="accent"
            disabled={!accepted || busy}
            onClick={attestNow}
            data-testid="take-on-attest"
          >
            {busy ? "Attesting…" : "Attest"}
          </Button>
        </div>
      ) : (
        validated && (
          <p
            className="text-sm text-slate-700 dark:text-slate-300"
            data-testid="take-on-attestation-unavailable"
          >
            {allowed.has("ATTEST")
              ? NO_STATEMENT
              : batch.counts.valid === 0
                ? NOTHING_TO_ATTEST
                : NOT_PERMITTED}
          </p>
        )
      )}

      {error && <TakeOnError message={error} testId="take-on-attestation-error" />}
    </div>
  );
}
