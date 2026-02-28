"use client";

import { useState, useEffect } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Table, TableRow, TableCell, TableHeader } from "@tiptap/extension-table";
import LinkExtension from "@tiptap/extension-link";
import UnderlineExtension from "@tiptap/extension-underline";
import Placeholder from "@tiptap/extension-placeholder";
import { Plus } from "lucide-react";
import { VariableExtension } from "./extensions/variable";
import { LoopTableExtension } from "./extensions/loopTable";
import { ClauseBlockExtension } from "./extensions/clauseBlock";
import { ClausePicker } from "./ClausePicker";
import { EditorToolbar } from "./EditorToolbar";
import type { TemplateEntityType } from "@/lib/types";
import "./editor.css";

interface DocumentEditorProps {
  content?: Record<string, unknown> | null;
  onUpdate?: (json: Record<string, unknown>) => void;
  scope?: "template" | "clause";
  editable?: boolean;
  entityType?: TemplateEntityType;
}

export function DocumentEditor({
  content,
  onUpdate,
  scope = "template",
  editable = true,
  entityType,
}: DocumentEditorProps) {
  const [addClauseOpen, setAddClauseOpen] = useState(false);
  const placeholderText =
    scope === "clause" ? "Enter clause content..." : "Start typing...";

  const customExtensions =
    scope === "template"
      ? [VariableExtension, LoopTableExtension, ClauseBlockExtension]
      : [VariableExtension];

  const editor = useEditor({
    extensions: [
      StarterKit,
      Table.configure({ resizable: true }),
      TableRow,
      TableCell,
      TableHeader,
      LinkExtension.configure({
        openOnClick: false,
        validate: (url: string) => /^https?:\/\//.test(url),
      }),
      UnderlineExtension,
      Placeholder.configure({ placeholder: placeholderText }),
      ...customExtensions,
    ],
    content: content ?? undefined,
    editable,
    onUpdate: ({ editor: ed }) => {
      onUpdate?.(ed.getJSON() as Record<string, unknown>);
    },
  });

  useEffect(() => {
    if (editor && content) {
      // Only update content if it differs from current editor state
      const currentJSON = JSON.stringify(editor.getJSON());
      const newJSON = JSON.stringify(content);
      if (currentJSON !== newJSON) {
        editor.commands.setContent(content);
      }
    }
  }, [editor, content]);

  useEffect(() => {
    if (editor) {
      editor.setEditable(editable);
    }
  }, [editor, editable]);

  const showAddClause = editable && scope === "template";

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      {editable && <EditorToolbar editor={editor} entityType={entityType} />}
      <div className="editor-content-wrapper p-6">
        <EditorContent editor={editor} />
      </div>
      {showAddClause && (
        <>
          <div className="border-t border-slate-100 px-6 py-3 dark:border-slate-800">
            <button
              type="button"
              onClick={() => setAddClauseOpen(true)}
              className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
            >
              <Plus className="size-3.5" />
              Add Clause
            </button>
          </div>
          <ClausePicker
            open={addClauseOpen}
            onOpenChange={setAddClauseOpen}
            onSelect={(clause) => {
              if (!editor) return;
              editor
                .chain()
                .focus()
                .insertContent({
                  type: "clauseBlock",
                  attrs: {
                    clauseId: clause.id,
                    slug: clause.slug,
                    title: clause.title,
                    required: clause.required,
                  },
                })
                .run();
            }}
          />
        </>
      )}
    </div>
  );
}
