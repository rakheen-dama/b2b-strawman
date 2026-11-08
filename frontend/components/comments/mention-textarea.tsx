"use client";

import { useId, useRef, useState, type ComponentProps, type KeyboardEvent } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import {
  filterMentionCandidates,
  findMentionQuery,
  insertMention,
  type MentionCandidate,
  type MentionQuery,
} from "@/lib/comments";

interface MentionTextareaProps extends Omit<ComponentProps<"textarea">, "value" | "onChange"> {
  value: string;
  onValueChange: (value: string) => void;
  /** Members who can be mentioned; an empty list disables the suggestions. */
  candidates: MentionCandidate[];
}

function sameQuery(a: MentionQuery | null, b: MentionQuery | null): boolean {
  return a?.start === b?.start && a?.query === b?.query;
}

/**
 * A comment textarea with @-mention autocomplete (634C). Typing "@" plus the start of a name lists
 * the matching members; ArrowUp/ArrowDown move, Enter or Tab inserts "@First Last ", Escape closes.
 * Focus stays in the textarea throughout. The backend resolves "@First Last" (or "@First") to the
 * member when the comment is posted, so the inserted text is the whole contract — no rich-text node.
 *
 * Accessibility: the textarea is an ARIA 1.2 combobox (keeping its label as the accessible name)
 * that controls a listbox; the active option is exposed through aria-activedescendant and the
 * suggestion count through a polite live region. The listbox is rendered here rather than with
 * cmdk, whose items assign their own generated ids, so option ids are deterministic.
 */
export function MentionTextarea({
  value,
  onValueChange,
  candidates,
  onKeyDown,
  ...textareaProps
}: MentionTextareaProps) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const listId = useId();
  const [mention, setMention] = useState<MentionQuery | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const matches = mention ? filterMentionCandidates(candidates, mention.query) : [];
  const open = matches.length > 0;
  const active = open ? matches[Math.min(activeIndex, matches.length - 1)] : undefined;
  const optionId = (candidate: MentionCandidate) => `${listId}-${candidate.id}`;
  // Announced only while an @-token is being typed and there is anyone to mention.
  const announcement =
    mention && candidates.length > 0
      ? matches.length > 0
        ? `${matches.length} ${matches.length === 1 ? "suggestion" : "suggestions"}`
        : "No matches"
      : "";

  function track(text: string, cursor: number | null) {
    const next = cursor === null ? null : findMentionQuery(text, cursor);
    if (!sameQuery(next, mention)) {
      setMention(next);
      setActiveIndex(0);
    }
  }

  function choose(candidate: MentionCandidate) {
    const textarea = ref.current;
    if (!mention || !textarea) return;
    const result = insertMention(value, mention.start, textarea.selectionStart, candidate.name);
    onValueChange(result.text);
    setMention(null);
    // Restore the caret after React commits the new value.
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(result.cursor, result.cursor);
    });
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // An IME is composing (e.g. Enter confirms a candidate in the IME): leave the keys to it.
    if (open && !e.nativeEvent.isComposing) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        const step = e.key === "ArrowDown" ? 1 : -1;
        setActiveIndex((i) => (i + step + matches.length) % matches.length);
        return;
      }
      // Plain Tab only: Shift+Tab keeps moving focus backwards.
      if ((e.key === "Enter" || (e.key === "Tab" && !e.shiftKey)) && active) {
        e.preventDefault();
        choose(active);
        return;
      }
      if (e.key === "Escape") {
        // Close only the suggestions — never the dialog the textarea may sit in.
        e.preventDefault();
        e.stopPropagation();
        setMention(null);
        return;
      }
    }
    onKeyDown?.(e);
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (!next) setMention(null);
      }}
    >
      <PopoverAnchor asChild>
        <Textarea
          {...textareaProps}
          ref={ref}
          value={value}
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-autocomplete="list"
          aria-controls={open ? listId : undefined}
          aria-activedescendant={active ? optionId(active) : undefined}
          onChange={(e) => {
            onValueChange(e.target.value);
            track(e.target.value, e.target.selectionStart);
          }}
          onSelect={(e) => track(e.currentTarget.value, e.currentTarget.selectionStart)}
          onKeyDown={handleKeyDown}
        />
      </PopoverAnchor>
      <span
        aria-live="polite"
        aria-atomic="true"
        className="sr-only"
        data-slot="mention-announcement"
      >
        {announcement}
      </span>
      <PopoverContent
        align="start"
        className="w-64 p-1"
        // Keep the caret in the textarea: the list is driven from its key handler.
        onOpenAutoFocus={(e) => e.preventDefault()}
        onCloseAutoFocus={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => {
          // Escape closes the suggestions; stop it here reaching a parent dialog.
          e.preventDefault();
          setMention(null);
        }}
        onInteractOutside={(e) => {
          // A click back in the textarea only moves the caret; onSelect re-evaluates the token.
          if (e.target instanceof Node && ref.current?.contains(e.target)) e.preventDefault();
        }}
      >
        <ul
          id={listId}
          role="listbox"
          aria-label="Mention a member"
          className="max-h-64 overflow-y-auto"
        >
          {matches.map((candidate, index) => {
            const selected = candidate.id === active?.id;
            return (
              <li
                key={candidate.id}
                id={optionId(candidate)}
                role="option"
                aria-selected={selected}
                // Keep focus (and the caret) in the textarea when the option is clicked.
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => choose(candidate)}
                className={cn(
                  "cursor-default rounded-sm px-2 py-1.5 text-sm select-none",
                  selected && "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100"
                )}
              >
                {candidate.name}
              </li>
            );
          })}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
