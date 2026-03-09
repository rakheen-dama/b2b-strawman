"use client";

import { useEffect } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Table, TableRow, TableCell, TableHeader } from "@tiptap/extension-table";
import LinkExtension from "@tiptap/extension-link";
import UnderlineExtension from "@tiptap/extension-underline";
import Placeholder from "@tiptap/extension-placeholder";
import { VariableExtension } from "./extensions/variable";
import { LoopTableExtension } from "./extensions/loopTable";
import { ClauseBlockExtension } from "./extensions/clauseBlock";
import { ConditionalBlockExtension } from "./extensions/conditionalBlock";
import { EditorToolbar } from "./EditorToolbar";
import { MissingVariablesContext } from "./MissingVariablesContext";
import type { TemplateEntityType } from "@/lib/types";
import "./editor.css";

const EMPTY_SET = new Set<string>();

interface DocumentEditorProps {
  content?: Record<string, unknown> | null;
  onUpdate?: (json: Record<string, unknown>) => void;
  scope?: "template" | "clause";
  editable?: boolean;
  entityType?: TemplateEntityType;
  missingVariables?: Set<string>;
}

export function DocumentEditor({
  content,
  onUpdate,
  scope = "template",
  editable = true,
  entityType,
  missingVariables,
}: DocumentEditorProps) {
  const placeholderText =
    scope === "clause" ? "Enter clause content..." : "Start typing...";

  const customExtensions =
    scope === "template"
      ? [VariableExtension, LoopTableExtension, ClauseBlockExtension, ConditionalBlockExtension]
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
    immediatelyRender: false,
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

  return (
    <MissingVariablesContext.Provider value={missingVariables ?? EMPTY_SET}>
      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        {editable && <EditorToolbar editor={editor} entityType={entityType} scope={scope} />}
        <div className="editor-content-wrapper p-6">
          <EditorContent editor={editor} />
        </div>
      </div>
    </MissingVariablesContext.Provider>
  );
}
