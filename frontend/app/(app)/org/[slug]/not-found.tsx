"use client";

import { useParams } from "next/navigation";
import { FileQuestion } from "lucide-react";
import { Button } from "@b2mash/ui/button";

/**
 * The org shell's 404 (K-63): rendered inside <main id="main-content"> when a page under
 * /org/[slug] calls notFound() — e.g. a member's deep link to a Fees or Compliance page, or a
 * handleApiError 404 — instead of the layout ErrorBoundary's "Something went wrong" panel. Exactly
 * one h1 (sidebar-navigation.spec.ts:38-39 invariant). Nearer not-found files (matters/[id]) win.
 * A not-found file receives no params, so the slug comes from useParams. "Back to Home" is a plain
 * <a> (full navigation), as in matters/[id]/not-found.tsx.
 */
export default function OrgNotFound() {
  const { slug } = useParams<{ slug: string }>();
  return (
    <div data-testid="org-not-found" className="flex flex-col items-center gap-4 py-24 text-center">
      <FileQuestion className="text-muted-foreground size-12" aria-hidden="true" />
      <h1 className="font-display text-foreground text-xl">Page not found</h1>
      <p className="text-muted-foreground max-w-md text-sm">
        {"This page doesn't exist, or you don't have access to it."}
      </p>
      <Button asChild size="sm" variant="outline">
        <a href={`/org/${slug}/dashboard`}>Back to Home</a>
      </Button>
    </div>
  );
}
