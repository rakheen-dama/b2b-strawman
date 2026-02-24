"use client";

import Link from "next/link";
import { FileText, Calendar } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { PortalProject } from "@/lib/types";

interface ProjectCardProps {
  project: PortalProject;
}

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);
  const diffMonths = Math.floor(diffDays / 30);
  const diffYears = Math.floor(diffDays / 365);

  if (diffYears > 0) return `${diffYears} year${diffYears > 1 ? "s" : ""} ago`;
  if (diffMonths > 0)
    return `${diffMonths} month${diffMonths > 1 ? "s" : ""} ago`;
  if (diffDays > 0) return `${diffDays} day${diffDays > 1 ? "s" : ""} ago`;
  if (diffHours > 0) return `${diffHours} hour${diffHours > 1 ? "s" : ""} ago`;
  if (diffMinutes > 0)
    return `${diffMinutes} minute${diffMinutes > 1 ? "s" : ""} ago`;
  return "just now";
}

export function ProjectCard({ project }: ProjectCardProps) {
  return (
    <Link href={`/projects/${project.id}`} className="block">
      <Card className="transition-shadow hover:shadow-md">
        <CardHeader>
          <CardTitle className="text-base">{project.name}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {project.description && (
            <p className="line-clamp-2 text-sm text-slate-600">
              {project.description}
            </p>
          )}
          <div className="flex items-center gap-4 text-xs text-slate-500">
            <span className="flex items-center gap-1">
              <FileText className="size-3.5" />
              {project.documentCount} document
              {project.documentCount !== 1 ? "s" : ""}
            </span>
            <span className="flex items-center gap-1">
              <Calendar className="size-3.5" />
              {formatRelativeTime(project.createdAt)}
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
