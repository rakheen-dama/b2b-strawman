"use client";

import { useState } from "react";
import { NodeViewWrapper, type NodeViewProps } from "@tiptap/react";
import {
  GripVertical,
  MoreVertical,
  ChevronDown,
  ChevronRight,
  Trash2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useClauseContent } from "../hooks/useClauseContent";
import { extractTextFromBody } from "@/lib/tiptap-utils";

export function ClauseBlockNodeView({ node, deleteNode }: NodeViewProps) {
  const [expanded, setExpanded] = useState(false);
  const clauseId = (node.attrs.clauseId ?? "") as string;
  const title = (node.attrs.title ?? "") as string;
  const required = (node.attrs.required ?? false) as boolean;

  const { body, isLoading } = useClauseContent(clauseId);

  // Extract text from Tiptap JSON for preview
  const textPreview = body
    ? extractTextFromBody(body)
    : null;

  return (
    <NodeViewWrapper className="my-4">
      <div className="rounded-lg border border-slate-200 border-l-4 border-l-teal-500 bg-white dark:border-slate-800 dark:border-l-teal-500 dark:bg-slate-950">
        {/* Title bar */}
        <div className="flex items-center gap-2 border-b border-slate-200 px-3 py-2 dark:border-slate-800">
          <div
            className="cursor-grab text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
            data-drag-handle=""
            contentEditable={false}
          >
            <GripVertical className="h-4 w-4" />
          </div>

          <span className="flex-1 truncate text-sm font-medium text-slate-700 dark:text-slate-200">
            {title || "Untitled Clause"}
          </span>

          {required && (
            <Badge variant="pro" className="text-xs">
              Required
            </Badge>
          )}

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon-xs">
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                className="text-red-600 dark:text-red-400"
                onClick={() => deleteNode()}
              >
                <Trash2 className="mr-2 h-4 w-4" />
                Remove clause
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* Body section */}
        <div className="px-3 py-2">
          {isLoading ? (
            <div className="text-sm text-slate-400">Loading clause content...</div>
          ) : textPreview ? (
            <>
              <button
                type="button"
                className="mb-1 flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? (
                  <ChevronDown className="h-3 w-3" />
                ) : (
                  <ChevronRight className="h-3 w-3" />
                )}
                {expanded ? "Collapse" : "Expand"} preview
              </button>
              {expanded && (
                <div className="max-h-48 overflow-y-auto rounded bg-slate-50 p-2 text-sm text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                  {textPreview}
                </div>
              )}
            </>
          ) : (
            <div className="text-sm text-slate-400">No content available</div>
          )}
        </div>
      </div>
    </NodeViewWrapper>
  );
}
