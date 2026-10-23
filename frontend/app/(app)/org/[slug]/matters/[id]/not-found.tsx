"use client";

import { useParams } from "next/navigation";
import { FolderLock } from "lucide-react";
import { Button } from "@b2mash/ui/button";

/**
 * Rendered when matters/[id]/page.tsx calls notFound() — a matter that does not exist or that the
 * viewer is not on (GET /api/matters/{id} → 404; ADR-346, Phase 88 OQ #4). A not-found file receives
 * no params, so the org slug comes from useParams (hence "use client").
 *
 * "Back to Matters" is a plain <a> (full document navigation), not next/link: when this page is the
 * result of a direct load, notFound() is streamed inside the matters loading.tsx Suspense boundary
 * (HTTP 200) and the client router no longer commits soft navigations on Next 16.2.6 — next/link and
 * the sidebar do nothing. Observed in 619D; tracked as a follow-up.
 */
export default function MatterNotFound() {
  const { slug } = useParams<{ slug: string }>();

  return (
    <div
      data-testid="matter-not-found"
      className="flex flex-col items-center gap-4 py-24 text-center"
    >
      <FolderLock className="size-16 text-slate-300 dark:text-slate-700" aria-hidden="true" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
        {"This matter isn't on your list"}
      </h2>
      <p className="max-w-md text-sm text-slate-600 dark:text-slate-400">
        {
          "It may not exist, or you haven't been added to its team. Ask the matter lead or a firm admin to add you."
        }
      </p>
      <Button asChild size="sm" variant="outline">
        <a href={`/org/${slug}/matters`}>Back to Matters</a>
      </Button>
    </div>
  );
}
