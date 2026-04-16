"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Check, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { PackCatalogEntry, UninstallCheck } from "@/lib/api/packs";

function formatPackType(type: string): string {
  switch (type) {
    case "DOCUMENT_TEMPLATE":
      return "Document Templates";
    case "AUTOMATION_TEMPLATE":
      return "Automation Templates";
    default:
      return type;
  }
}

function formatProfileAffinity(profile: string | null): string {
  if (!profile) return "Universal";
  return profile
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" - ");
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

interface PackCardProps {
  pack: PackCatalogEntry;
  variant: "available" | "installed";
  onInstall?: (packId: string) => void;
  onUninstall?: (packId: string) => void;
  uninstallCheck?: UninstallCheck | null;
  isInstalling?: boolean;
  isUninstalling?: boolean;
  slug: string;
}

export function PackCard({
  pack,
  variant,
  onInstall,
  onUninstall,
  uninstallCheck,
  isInstalling,
  isUninstalling,
  slug,
}: PackCardProps) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const itemLabel =
    pack.type === "DOCUMENT_TEMPLATE" ? "templates" : "rules";

  async function handleUninstallConfirm() {
    setIsSubmitting(true);
    try {
      onUninstall?.(pack.packId);
    } finally {
      setIsSubmitting(false);
      setDialogOpen(false);
    }
  }

  const canUninstall = uninstallCheck?.canUninstall !== false;

  return (
    <>
      <Card data-testid={`pack-card-${pack.packId}`}>
        <CardContent className="space-y-3 p-4">
          <div className="flex items-start justify-between gap-2">
            <div className="space-y-1">
              <h3 className="font-semibold text-slate-950 dark:text-slate-50">
                {pack.name}
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                v{pack.version}
              </p>
            </div>
          </div>

          <p className="text-sm text-slate-600 dark:text-slate-400">
            {pack.description}
          </p>

          <div className="flex flex-wrap gap-1.5">
            <Badge variant="secondary">
              {pack.itemCount} {itemLabel}
            </Badge>
            <Badge variant="outline">{formatPackType(pack.type)}</Badge>
            <Badge variant="outline">
              {formatProfileAffinity(pack.verticalProfile)}
            </Badge>
          </div>

          {variant === "available" && (
            <div className="pt-1">
              {pack.installed ? (
                <Button variant="outline" size="sm" disabled>
                  <Check className="mr-1.5 size-3.5" />
                  Installed
                </Button>
              ) : (
                <Button
                  size="sm"
                  onClick={() => onInstall?.(pack.packId)}
                  disabled={isInstalling}
                >
                  {isInstalling && (
                    <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                  )}
                  Install
                </Button>
              )}
            </div>
          )}

          {variant === "installed" && (
            <div className="flex items-center justify-between pt-1">
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Installed on{" "}
                {pack.installedAt ? formatDate(pack.installedAt) : "unknown"}
              </p>
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!canUninstall || isUninstalling}
                        onClick={() => setDialogOpen(true)}
                      >
                        {isUninstalling && (
                          <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                        )}
                        Uninstall
                      </Button>
                    </span>
                  </TooltipTrigger>
                  {!canUninstall && uninstallCheck?.blockingReason && (
                    <TooltipContent className="max-w-xs">
                      {uninstallCheck.blockingReason}
                    </TooltipContent>
                  )}
                </Tooltip>
              </TooltipProvider>
            </div>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Uninstall Pack</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove {pack.itemCount} {itemLabel} created by this
              pack. Only allowed because none have been used or edited. Continue?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isSubmitting}>
              Cancel
            </AlertDialogCancel>
            <Button
              variant="destructive"
              onClick={handleUninstallConfirm}
              disabled={isSubmitting}
            >
              {isSubmitting ? "Uninstalling..." : "Uninstall"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
