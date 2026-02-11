import { auth } from "@clerk/nextjs/server";
import { api, ApiError, handleApiError } from "@/lib/api";
import type { Project } from "@/lib/types";
import type { BillingResponse } from "@/lib/internal-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatRelativeDate } from "@/lib/format";
import {
  FolderOpen,
  FileText,
  Users,
  HardDrive,
  Plus,
  UserPlus,
  Upload,
  Loader2,
} from "lucide-react";
import Link from "next/link";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";

export default async function OrgDashboardPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgSlug } = await auth();

  let projects: Project[] = [];
  let memberCount = 0;

  try {
    const [projectsData, billing] = await Promise.all([
      api.get<Project[]>("/api/projects"),
      api.get<BillingResponse>("/api/billing/subscription").catch((e) => {
        console.error("Failed to fetch billing data:", e);
        return null;
      }),
    ]);
    projects = projectsData;
    memberCount = billing?.limits.currentMembers ?? 0;
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
          <Loader2 className="text-muted-foreground size-8 animate-spin" />
          <div className="text-center">
            <h2 className="text-lg font-semibold">Setting up your workspace</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              This usually takes just a few seconds.
            </p>
          </div>
          <ProvisioningPendingRefresh />
        </div>
      );
    }
    handleApiError(error);
  }

  const recentProjects = [...projects]
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .slice(0, 5);

  const stats = [
    { label: "Projects", value: projects.length, icon: FolderOpen },
    { label: "Documents", value: "\u2014", icon: FileText },
    { label: "Team Members", value: memberCount, icon: Users },
    { label: "Storage Used", value: "\u2014", icon: HardDrive },
  ];

  const placeholderActivity = [
    { actor: "Alice", action: "uploaded document.pdf", time: "2 hours ago" },
    { actor: "Bob", action: "joined Project X", time: "4 hours ago" },
    { actor: "Carol", action: "created Project Y", time: "yesterday" },
  ];

  return (
    <div className="space-y-8">
      {/* Page Header (33.1) */}
      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">Dashboard</h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">{orgSlug}</p>
      </div>

      {/* Stat Cards (33.1) */}
      <div className="grid grid-cols-2 gap-6 lg:grid-cols-4">
        {stats.map((stat) => (
          <div
            key={stat.label}
            className="rounded-lg border border-olive-200 bg-white p-6 dark:border-olive-800 dark:bg-olive-950"
          >
            <div className="flex items-center justify-between">
              <span className="text-sm text-olive-600 dark:text-olive-400">{stat.label}</span>
              <stat.icon className="size-4 text-olive-400 dark:text-olive-600" />
            </div>
            <p className="font-display mt-2 text-3xl text-olive-950 dark:text-olive-50">
              {stat.value}
            </p>
          </div>
        ))}
      </div>

      {/* Quick Actions (33.2) */}
      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link href={`/org/${slug}/projects`}>
            <Plus className="size-4" />
            New Project
          </Link>
        </Button>
        <Button asChild variant="soft">
          <Link href={`/org/${slug}/team`}>
            <UserPlus className="size-4" />
            Invite Member
          </Link>
        </Button>
        <Button asChild variant="soft">
          <Link href={`/org/${slug}/projects`}>
            <Upload className="size-4" />
            Upload Document
          </Link>
        </Button>
      </div>

      {/* Content: Recent Projects + Activity Feed (33.2, 33.3) */}
      <div className="grid grid-cols-1 gap-8 xl:grid-cols-[1fr_320px]">
        {/* Recent Projects Table (33.2) */}
        <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
          <div className="px-6 pt-6 pb-4">
            <h2 className="font-semibold text-olive-900 dark:text-olive-100">Recent Projects</h2>
          </div>

          {recentProjects.length === 0 ? (
            <div className="px-6 pb-6">
              <p className="text-sm text-olive-600 dark:text-olive-400">
                No projects yet.{" "}
                <Link
                  href={`/org/${slug}/projects`}
                  className="text-olive-900 underline underline-offset-4 dark:text-olive-100"
                >
                  Create your first project
                </Link>
              </p>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-olive-200 dark:border-olive-800">
                      <th className="px-6 py-2 text-left text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Name
                      </th>
                      <th className="hidden px-6 py-2 text-left text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                        Role
                      </th>
                      <th className="hidden px-6 py-2 text-left text-xs uppercase tracking-wide text-olive-600 md:table-cell dark:text-olive-400">
                        Documents
                      </th>
                      <th className="px-6 py-2 text-left text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                        Last Updated
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentProjects.map((project) => (
                      <tr
                        key={project.id}
                        className="border-b border-olive-100 transition-colors last:border-0 hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                      >
                        <td className="px-6 py-3">
                          <Link
                            href={`/org/${slug}/projects/${project.id}`}
                            className="font-medium text-olive-950 hover:underline dark:text-olive-50"
                          >
                            {project.name}
                          </Link>
                        </td>
                        <td className="hidden px-6 py-3 sm:table-cell">
                          {project.projectRole ? (
                            <Badge variant={project.projectRole}>{project.projectRole}</Badge>
                          ) : (
                            <span className="text-sm text-olive-400">&mdash;</span>
                          )}
                        </td>
                        <td className="hidden px-6 py-3 text-sm text-olive-600 md:table-cell dark:text-olive-400">
                          &mdash;
                        </td>
                        <td className="px-6 py-3 text-sm text-olive-600 dark:text-olive-400">
                          {formatRelativeDate(project.updatedAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {projects.length > 5 && (
                <div className="px-6 py-4">
                  <Link
                    href={`/org/${slug}/projects`}
                    className="text-sm text-olive-600 transition-colors hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
                  >
                    View all projects &rarr;
                  </Link>
                </div>
              )}
            </>
          )}
        </div>

        {/* Activity Feed (33.3) */}
        <div className="rounded-lg border border-olive-200 bg-white p-6 dark:border-olive-800 dark:bg-olive-950">
          <h2 className="font-semibold text-olive-900 dark:text-olive-100">Recent Activity</h2>
          <div className="mt-4 border-l-2 border-olive-200 dark:border-olive-700">
            {placeholderActivity.map((entry, i) => (
              <div key={i} className="py-2 pl-4">
                <p className="text-xs text-olive-400 dark:text-olive-500">{entry.time}</p>
                <p className="mt-0.5 text-sm text-olive-700 dark:text-olive-300">
                  <span className="font-medium text-olive-900 dark:text-olive-100">
                    {entry.actor}
                  </span>{" "}
                  {entry.action}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
