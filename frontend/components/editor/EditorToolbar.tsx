"use client";

import { useState } from "react";
import type { Editor } from "@tiptap/react";
import {
  Bold,
  Italic,
  Underline,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Table2,
  Minus,
  Link,
  Braces,
  FileText,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import type { TemplateEntityType } from "@/lib/types";
import { VariablePicker } from "./VariablePicker";
import { ClausePicker } from "./ClausePicker";

interface EditorToolbarProps {
  editor: Editor | null;
  entityType?: TemplateEntityType;
}

export function EditorToolbar({ editor, entityType }: EditorToolbarProps) {
  const [variablePickerOpen, setVariablePickerOpen] = useState(false);
  const [clausePickerOpen, setClausePickerOpen] = useState(false);

  if (!editor) return null;

  return (
    <>
      <div className="flex items-center gap-0.5 border-b border-slate-200 bg-slate-50 px-2 py-1 dark:border-slate-800 dark:bg-slate-900">
        {/* Text formatting */}
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleBold().run()}
          className={cn(editor.isActive("bold") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Bold"
        >
          <Bold className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleItalic().run()}
          className={cn(editor.isActive("italic") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Italic"
        >
          <Italic className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleUnderline().run()}
          className={cn(editor.isActive("underline") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Underline"
        >
          <Underline className="size-3.5" />
        </Button>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Headings */}
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
          className={cn(
            editor.isActive("heading", { level: 1 }) && "bg-slate-200 dark:bg-slate-700",
          )}
          aria-label="Heading 1"
        >
          <Heading1 className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          className={cn(
            editor.isActive("heading", { level: 2 }) && "bg-slate-200 dark:bg-slate-700",
          )}
          aria-label="Heading 2"
        >
          <Heading2 className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          className={cn(
            editor.isActive("heading", { level: 3 }) && "bg-slate-200 dark:bg-slate-700",
          )}
          aria-label="Heading 3"
        >
          <Heading3 className="size-3.5" />
        </Button>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Lists */}
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          className={cn(editor.isActive("bulletList") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Bullet list"
        >
          <List className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          className={cn(editor.isActive("orderedList") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Ordered list"
        >
          <ListOrdered className="size-3.5" />
        </Button>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Insert */}
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() =>
            editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
          }
          aria-label="Insert table"
        >
          <Table2 className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => editor.chain().focus().setHorizontalRule().run()}
          aria-label="Horizontal rule"
        >
          <Minus className="size-3.5" />
        </Button>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Link */}
        <Button
          variant="ghost"
          size="icon-xs"
          type="button"
          onClick={() => {
            if (editor.isActive("link")) {
              editor.chain().focus().unsetLink().run();
            } else {
              // TODO(213B+): Replace window.prompt with a Popover/Dialog for link input
              const url = window.prompt("Enter URL:");
              if (url && /^https?:\/\//.test(url)) {
                editor.chain().focus().setLink({ href: url }).run();
              }
            }
          }}
          className={cn(editor.isActive("link") && "bg-slate-200 dark:bg-slate-700")}
          aria-label="Link"
        >
          <Link className="size-3.5" />
        </Button>

        {/* Variable Picker — only shown in template scope */}
        {entityType && (
          <>
            <Separator orientation="vertical" className="mx-1 h-5" />
            <Button
              variant="ghost"
              size="icon-xs"
              type="button"
              onClick={() => setVariablePickerOpen(true)}
              aria-label="Insert variable"
              title="Insert variable"
            >
              <Braces className="size-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon-xs"
              type="button"
              onClick={() => setClausePickerOpen(true)}
              aria-label="Insert clause"
              title="Insert clause"
            >
              <FileText className="size-3.5" />
            </Button>
          </>
        )}
      </div>

      {/* Picker dialogs — rendered outside toolbar div to avoid nesting issues */}
      {entityType && (
        <>
          <VariablePicker
            entityType={entityType}
            open={variablePickerOpen}
            onOpenChange={setVariablePickerOpen}
            onSelect={(key) => {
              editor.chain().focus().insertContent({ type: "variable", attrs: { key } }).run();
            }}
          />
          <ClausePicker
            open={clausePickerOpen}
            onOpenChange={setClausePickerOpen}
            onSelect={(clause) => {
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
    </>
  );
}
