import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { getTags } from "@/lib/api";
import { TagsContent } from "./tags-content";
import type { TagResponse } from "@/lib/types";

export default async function TagsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let tags: TagResponse[] = [];
  try {
    tags = await getTags();
  } catch {
    // Non-fatal: show empty state on API failure
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          Tags
        </h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
          Manage tags for projects, tasks, and customers.
        </p>
      </div>

      <TagsContent slug={slug} tags={tags} canManage={isAdmin} />
    </div>
  );
}
