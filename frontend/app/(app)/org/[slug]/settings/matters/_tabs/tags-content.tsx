"use client";

import { useMemo } from "react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { createMessages } from "@/lib/messages";
import { TagDialog } from "@/components/tags/TagDialog";
import { DeleteTagDialog } from "@/components/tags/DeleteTagDialog";
import { Pencil, Plus, Trash2, Tag } from "lucide-react";
import type { TagResponse } from "@/lib/types";

/** sRGB channel (0-255) → linear light (WCAG 2.x relative luminance). */
function linear(channel: number): number {
  const c = channel / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** WCAG 2.x relative luminance of an sRGB colour (channels 0-255). */
function relativeLuminance(r: number, g: number, b: number): number {
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

/** WCAG contrast ratio between two relative luminances. */
function contrastRatio(a: number, b: number): number {
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

const WHITE_LUMINANCE = 1;
// `--slate-950` is oklch(13.5% 0.006 258) (packages/ui/styles/globals.css) ≈ sRGB #07080B.
const SLATE_950_LUMINANCE = relativeLuminance(7, 8, 11);

/**
 * Text colour for a tag chip filled with `hexColor` (K-15): whichever of near-black (`--slate-950`)
 * and white has the higher WCAG contrast ratio against the fill. Theme tokens, no raw hex; an
 * unparseable colour gets white, as before.
 */
export function getContrastColor(hexColor: string): string {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hexColor.slice(i, i + 2), 16));
  const luminance = relativeLuminance(r, g, b);
  // NaN (unparseable) compares false → white.
  return contrastRatio(luminance, SLATE_950_LUMINANCE) > contrastRatio(luminance, WHITE_LUMINANCE)
    ? "var(--slate-950)"
    : "var(--color-white)";
}

const getTagId = (tag: TagResponse) => tag.id;

interface TagsContentProps {
  slug: string;
  tags: TagResponse[];
  canManage: boolean;
}

/**
 * Matters › Tags list (Phase 91 665C): DataTable client mode in an untitled section; the "Add Tag"
 * button lives in the tab's `PageHeader` actions.
 */
export function TagsContent({ slug, tags, canManage }: TagsContentProps) {
  const columns = useMemo(
    () =>
      defineColumns<TagResponse>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name" },
          // The cell holds only the chip: e2e finds the row by the cell's exact name.
          cell: ({ row }) => {
            const tag = row.original;
            return (
              <div className="flex items-center gap-2">
                <Badge
                  variant="outline"
                  style={
                    tag.color
                      ? {
                          backgroundColor: tag.color,
                          color: getContrastColor(tag.color),
                          borderColor: tag.color,
                        }
                      : undefined
                  }
                >
                  {tag.name}
                </Badge>
              </div>
            );
          },
        },
        {
          id: "slug",
          meta: {
            label: "Slug",
            headerClassName: "hidden sm:table-cell",
            cellClassName: "text-muted-foreground hidden text-sm sm:table-cell",
          },
          cell: ({ row }) => (
            <code className="bg-muted rounded px-1.5 py-0.5 text-xs">{row.original.slug}</code>
          ),
        },
        {
          id: "color",
          meta: {
            label: "Color",
            headerClassName: "hidden md:table-cell",
            cellClassName: "text-muted-foreground hidden text-sm md:table-cell",
          },
          cell: ({ row }) =>
            row.original.color ? (
              <div className="flex items-center gap-2">
                <span
                  className="border-border inline-block size-4 rounded border"
                  style={{ backgroundColor: row.original.color }}
                />
                <code className="text-xs">{row.original.color}</code>
              </div>
            ) : (
              <span className="text-muted-foreground">&mdash;</span>
            ),
        },
        ...(canManage
          ? defineColumns<TagResponse>([
              {
                id: "actions",
                enableHiding: false,
                meta: { label: "Actions", align: "end" },
                cell: ({ row }) => {
                  const tag = row.original;
                  return (
                    <div className="flex items-center justify-end gap-1">
                      <TagDialog slug={slug} tag={tag}>
                        <Button variant="ghost" size="sm">
                          <Pencil className="size-4" />
                          <span className="sr-only">Edit {tag.name}</span>
                        </Button>
                      </TagDialog>
                      <DeleteTagDialog slug={slug} tagId={tag.id} tagName={tag.name}>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive dark:text-destructive dark:hover:bg-destructive/10 dark:hover:text-destructive"
                        >
                          <Trash2 className="size-4" />
                          <span className="sr-only">Delete {tag.name}</span>
                        </Button>
                      </DeleteTagDialog>
                    </div>
                  );
                },
              },
            ])
          : []),
      ]),
    [slug, canManage]
  );

  const { t } = createMessages("empty-states");
  const empty = (
    <EmptyState
      variant="default"
      icon={Tag}
      title={t("tags.list.heading")}
      description={canManage ? t("tags.list.description") : t("tags.list.descriptionMember")}
      action={
        canManage ? (
          <TagDialog slug={slug}>
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              {t("tags.list.cta")}
            </Button>
          </TagDialog>
        ) : undefined
      }
    />
  );
  // DA-7: an empty list is the whole tab panel's content, so no card around it.
  if (tags.length === 0) return empty;

  return (
    <SettingsSection>
      <DataTable
        columns={columns}
        data={tags}
        getRowId={getTagId}
        columnVisibility={false}
        pageSize={null}
        stickyHeader={false}
        frame={false}
        empty={empty}
      />
    </SettingsSection>
  );
}
