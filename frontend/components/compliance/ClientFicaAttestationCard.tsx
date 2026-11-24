import type { ReactNode } from "react";
import { FileCheck } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { FICA_STATUS_LABEL } from "@/lib/clients-list";
import { formatDateInSA } from "@/lib/format";
import { TAKE_ON_CLIENT_VALUE_LABEL } from "@/lib/types/take-on";
import {
  ficaReviewReasonLabels,
  type ClientFicaAttestation,
  type FicaStatus,
} from "@/lib/types/fica";

/**
 * The client's active FICA attestation from a take-on (Phase 93 655B, §17.3.5, ADR-388): every
 * field, who attested and when, and where the previous system's records are kept. Read from
 * `GET /api/clients/{clientId}/fica-attestation` (CUSTOMER_MANAGEMENT; the page renders the card
 * only when that read succeeds). Presentational and server-safe (no hooks).
 *
 * `ficaStatus` is the client's projected FICA status: the badge and the review reasons show only
 * while the attestation decides it (ATTESTED / ATTESTED_REVIEW). Once a Kazi onboarding checklist
 * or FICA request decides (FD-29), the card stays as the record but carries no badge.
 */
interface ClientFicaAttestationCardProps {
  attestation: ClientFicaAttestation;
  /** null = the status read failed or was not made. */
  ficaStatus: FicaStatus | null;
}

/** A coded value's label (655A take-on labels); an unknown code reads "—", never the raw enum. */
function codeLabel(group: string, value: string | null | undefined): string {
  if (!value) return "—";
  return TAKE_ON_CLIENT_VALUE_LABEL[group]?.[value] ?? "—";
}

function dateOrDash(value: string | null | undefined): string {
  return (value && formatDateInSA(value)) || "—";
}

function Field({
  testKey,
  label,
  children,
}: {
  testKey: string;
  label: string;
  children: ReactNode;
}) {
  return (
    <div data-testid={`fica-attestation-field-${testKey}`}>
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="text-sm text-slate-900 dark:text-slate-100">{children}</dd>
    </div>
  );
}

export function ClientFicaAttestationCard({
  attestation,
  ficaStatus,
}: ClientFicaAttestationCardProps) {
  const decides =
    ficaStatus !== null &&
    (ficaStatus.status === "ATTESTED" || ficaStatus.status === "ATTESTED_REVIEW") &&
    ficaStatus.attestationId === attestation.id;
  const reasons =
    decides && ficaStatus.status === "ATTESTED_REVIEW"
      ? ficaReviewReasonLabels(ficaStatus.reviewReasons)
      : [];
  const screening =
    attestation.screeningDate && attestation.screeningStatus !== "NOT_SCREENED"
      ? `${codeLabel("screeningStatus", attestation.screeningStatus)} · ${dateOrDash(attestation.screeningDate)}`
      : codeLabel("screeningStatus", attestation.screeningStatus);

  return (
    <Card data-testid="client-fica-attestation-card">
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-1.5 text-base">
            <FileCheck className="size-4 text-slate-400 dark:text-slate-500" aria-hidden="true" />
            FICA attestation
          </CardTitle>
          {decides && (
            <Badge
              variant={ficaStatus.status === "ATTESTED" ? "success" : "warning"}
              data-testid="fica-attestation-status"
            >
              {FICA_STATUS_LABEL[ficaStatus.status]}
            </Badge>
          )}
        </div>
        <p className="text-muted-foreground text-sm">
          CDD was done in the previous system. This client is not verified in Kazi.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {reasons.length > 0 && (
          <ul
            className="list-disc space-y-0.5 pl-4 text-sm text-slate-700 dark:text-slate-300"
            aria-label="Review reasons"
            data-testid="fica-attestation-review-reasons"
          >
            {reasons.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
        )}
        <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
          <Field testKey="client-type" label="Client type">
            {codeLabel("ficaClientType", attestation.ficaClientType)}
          </Field>
          <Field testKey="risk-rating" label="Risk rating">
            {codeLabel("riskRating", attestation.riskRating)}
          </Field>
          <Field testKey="cdd-completed" label="CDD completed">
            {dateOrDash(attestation.cddCompletedDate)}
          </Field>
          <Field testKey="last-review" label="Last review">
            {dateOrDash(attestation.lastReviewDate)}
          </Field>
          <Field testKey="next-review" label="Next review">
            {dateOrDash(attestation.nextReviewDate)}
          </Field>
          <Field testKey="beneficial-ownership" label="Beneficial ownership">
            {codeLabel("beneficialOwnershipStatus", attestation.beneficialOwnershipStatus)}
          </Field>
          <Field testKey="screening" label="Screening">
            {screening}
          </Field>
          <Field testKey="source-of-funds" label="Source of funds">
            {codeLabel("sourceOfFundsStatus", attestation.sourceOfFundsStatus)}
          </Field>
          <Field testKey="records-location" label="Records location">
            <span className="break-words">{attestation.cddRecordsLocation || "—"}</span>
          </Field>
          <Field testKey="source-system-ref" label="Previous system ID">
            {attestation.sourceSystemRef || "—"}
          </Field>
          <Field testKey="attested-by" label="Attested by">
            {attestation.attestedByRole
              ? `${attestation.attestedByName} (${attestation.attestedByRole})`
              : attestation.attestedByName}
          </Field>
          <Field testKey="attested-on" label="Attested on">
            {dateOrDash(attestation.attestedAt)}
          </Field>
          <Field testKey="statement-version" label="Statement version">
            {attestation.statementVersion}
          </Field>
        </dl>
      </CardContent>
    </Card>
  );
}
