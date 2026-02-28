import Link from "next/link";
import { FolderOpen, FileText } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import type { PortalProject } from "@/lib/types";

interface PortalProjectCardProps {
  project: PortalProject;
}

export function PortalProjectCard({ project }: PortalProjectCardProps) {
  return (
    <Link href={`/portal/projects/${project.id}`} className="group block">
      <Card className="transition-shadow duration-200 group-hover:shadow-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FolderOpen className="size-4 shrink-0 text-teal-600" />
            <span className="truncate">{project.name}</span>
          </CardTitle>
          {project.description && (
            <CardDescription className="line-clamp-2">
              {project.description}
            </CardDescription>
          )}
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-2">
            <Badge variant="neutral" className="gap-1">
              <FileText className="size-3" />
              {project.documentCount}{" "}
              {project.documentCount === 1 ? "document" : "documents"}
            </Badge>
          </div>
        </CardContent>
        <CardFooter className="text-xs text-slate-400">
          {project.createdAt && (
            <span>Created {formatDate(project.createdAt)}</span>
          )}
        </CardFooter>
      </Card>
    </Link>
  );
}
