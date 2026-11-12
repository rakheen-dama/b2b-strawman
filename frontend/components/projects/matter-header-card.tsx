"use client";

import { Fragment, type ReactNode } from "react";
import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { DetailHero, type HeroTile } from "@/components/shared/detail-hero";
import { MATTER_STATUS_BADGE } from "@/lib/constants/matter-status";
import { ProjectLifecycleActions } from "@/components/projects/project-lifecycle-actions";
import { MatterClosureAction } from "@/components/projects/matter-closure-action";
import { MatterReopenAction } from "@/components/projects/matter-reopen-action";
import { HeaderFactsStrip } from "@/components/shared/header-facts-strip";
import { formatDateInSA } from "@/lib/format";
import type { HeaderField } from "@/lib/constants/header-field-slugs";
import type { MatterStatus, Client } from "@/lib/types";

interface MatterHeaderCardProps {
  projectId: string;
  projectName: string;
  projectStatus: MatterStatus;
  workType: string | null;
  referenceNumber: string | null;
  closedAt: string | null;
  customers: Pick<Client, "id" | "name">[];
  slug: string;
  isAdmin: boolean;
  /** Case number / court / opposing party facts (HEADER_FIELD_SLUGS.PROJECT, Phase 88 G-B9). */
  headerFields?: HeaderField[];
  /** The matter's fee earner (its lead) for the subtitle; omitted when absent (641B). */
  feeEarnerName?: string | null;
  /** The matter's creation instant ("Opened <date>" in the subtitle); omitted when absent (641B). */
  openedAt?: string | null;
  /**
   * The hero KPI strip, built server-side by `matters/[id]/hero-tiles.ts` (ADR-373). Gated tiles
   * are already omitted; values are pre-formatted, so nothing here re-reads or re-gates.
   */
  tiles?: HeroTile[];
}

/**
 * The matter page hero (Phase 90 641B, ADR-373): identity, status, subtitle, lifecycle actions,
 * the header facts strip and the KPI tiles, composed on the shared DetailHero. Keeps every test id
 * the e2e suites rely on (`matter-header-card`, `matter-name`, `project-status-badge`,
 * `project-closed-at`, `matter-header-fields`, `header-lifecycle-actions`).
 */
export function MatterHeaderCard({
  projectId,
  projectName,
  projectStatus,
  workType,
  referenceNumber,
  closedAt,
  customers,
  slug,
  isAdmin,
  headerFields = [],
  feeEarnerName,
  openedAt,
  tiles,
}: MatterHeaderCardProps) {
  const statusBadge = MATTER_STATUS_BADGE[projectStatus];

  // Instants render on the SA calendar so SSR (UTC) and the browser agree (641A F2).
  const openedDate = openedAt ? formatDateInSA(openedAt) : "";
  const subtitleParts: { key: string; node: ReactNode }[] = [
    {
      key: "client",
      node:
        customers.length > 0 ? (
          <Link
            href={`/org/${slug}/clients/${customers[0].id}`}
            className="text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
          >
            {customers[0].name}
          </Link>
        ) : (
          "Internal Matter"
        ),
    },
  ];
  if (feeEarnerName) subtitleParts.push({ key: "fee-earner", node: feeEarnerName });
  if (openedDate) subtitleParts.push({ key: "opened", node: `Opened ${openedDate}` });

  return (
    <DetailHero
      data-testid="matter-header-card"
      titleTestId="matter-name"
      title={projectName}
      identifier={referenceNumber ?? undefined}
      status={
        <>
          <Badge variant={statusBadge.variant} data-testid="project-status-badge">
            {statusBadge.label}
          </Badge>
          {workType && <span className="text-muted-foreground text-sm">{workType}</span>}
          {projectStatus === "CLOSED" && closedAt && (
            <span className="text-muted-foreground text-sm" data-testid="project-closed-at">
              Closed {formatDateInSA(closedAt)}
            </span>
          )}
        </>
      }
      subtitle={subtitleParts.map((part, i) => (
        <Fragment key={part.key}>
          {i > 0 && (
            <span aria-hidden="true" className="mx-1.5">
              ·
            </span>
          )}
          {part.node}
        </Fragment>
      ))}
      facts={
        <HeaderFactsStrip
          fields={headerFields}
          testId="matter-header-fields"
          itemTestIdPrefix="matter-header-field"
        />
      }
      actions={
        <div className="flex shrink-0 items-center gap-2" data-testid="header-lifecycle-actions">
          {/* Self-gates on the matter_closure module, ACTIVE|COMPLETED and CLOSE_MATTER (G-D2). */}
          <MatterClosureAction
            slug={slug}
            projectId={projectId}
            projectName={projectName}
            projectStatus={projectStatus}
          />
          {projectStatus === "CLOSED" && (
            <MatterReopenAction
              slug={slug}
              projectId={projectId}
              projectName={projectName}
              projectStatus={projectStatus}
            />
          )}
          {(projectStatus === "ACTIVE" ||
            projectStatus === "COMPLETED" ||
            projectStatus === "ARCHIVED") &&
            isAdmin && (
              <ProjectLifecycleActions
                slug={slug}
                projectId={projectId}
                projectName={projectName}
                projectStatus={projectStatus}
              />
            )}
        </div>
      }
      tiles={tiles}
    />
  );
}
