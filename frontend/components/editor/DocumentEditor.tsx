"use client";

import { useEffect } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Table, TableRow, TableCell, TableHeader } from "@tiptap/extension-table";
import LinkExtension from "@tiptap/extension-link";
import UnderlineExtension from "@tiptap/extension-underline";
import Placeholder from "@tiptap/extension-placeholder";
import { EditorToolbar } from "./EditorToolbar";
import "./editor.css";

interface DocumentEditorProps {
  content?: Record<string, unknown> | null;
  onUpdate?: (json: Record<string, unknown>) => void;
  scope?: "template" | "clause";
  editable?: boolean;
}

export function DocumentEditor({
  content,
  onUpdate,
  scope = "template",
  editable = true,
}: DocumentEditorProps) {
  const placeholderText =
    scope === "clause" ? "Enter clause content..." : "Start typing...";

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

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      {editable && <EditorToolbar editor={editor} />}
      <div className="editor-content-wrapper p-6">
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
