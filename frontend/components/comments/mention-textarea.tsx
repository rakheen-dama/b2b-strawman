"use client";

import { useId, useRef, useState, type ComponentProps, type KeyboardEvent } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Command, CommandGroup, CommandItem, CommandList } from "@/components/ui/command";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
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
  const active = matches[Math.min(activeIndex, matches.length - 1)];

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
    if (open) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        const step = e.key === "ArrowDown" ? 1 : -1;
        setActiveIndex((i) => (i + step + matches.length) % matches.length);
        return;
      }
      if ((e.key === "Enter" || e.key === "Tab") && active) {
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
          aria-autocomplete="list"
          aria-controls={open ? listId : undefined}
          onChange={(e) => {
            onValueChange(e.target.value);
            track(e.target.value, e.target.selectionStart);
          }}
          onSelect={(e) => track(e.currentTarget.value, e.currentTarget.selectionStart)}
          onKeyDown={handleKeyDown}
        />
      </PopoverAnchor>
      <PopoverContent
        align="start"
        className="w-64 p-0"
        // Keep the caret in the textarea: the list is driven from its key handler.
        onOpenAutoFocus={(e) => e.preventDefault()}
        onCloseAutoFocus={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => {
          // Escape closes the suggestions; the textarea handler already did, so stop it here
          // reaching a parent dialog.
          e.preventDefault();
          setMention(null);
        }}
        onInteractOutside={(e) => {
          // A click back in the textarea only moves the caret; onSelect re-evaluates the token.
          if (e.target instanceof Node && ref.current?.contains(e.target)) e.preventDefault();
        }}
      >
        <Command shouldFilter={false} value={active?.id ?? ""} label="Mention a member">
          <CommandList id={listId}>
            <CommandGroup heading="Mention">
              {matches.map((candidate, index) => (
                <CommandItem
                  key={candidate.id}
                  value={candidate.id}
                  // Keep focus (and the caret) in the textarea when the item is clicked.
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseEnter={() => setActiveIndex(index)}
                  onSelect={() => choose(candidate)}
                >
                  {candidate.name}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
