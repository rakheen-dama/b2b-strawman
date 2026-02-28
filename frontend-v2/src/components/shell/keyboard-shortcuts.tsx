"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname, useParams } from "next/navigation";
import { ShortcutHelpDialog } from "./shortcut-help-dialog";

interface SequenceDetectorState {
  firstKey: string | null;
  timestamp: number;
}

/**
 * Global keyboard shortcuts listener component.
 *
 * Shortcuts:
 * - `?` — show help overlay with all shortcuts
 * - `c p` — create project (navigate to /org/{slug}/projects?create=true)
 * - `c t` — create task (dispatch CustomEvent for task creation)
 * - `c i` — create invoice (dispatch CustomEvent for invoice creation)
 * - `j` / `k` — navigate table rows (dispatch CustomEvent)
 * - `Enter` — open selected row (dispatch CustomEvent)
 * - `Escape` — close dialogs/overlays
 *
 * Two-key sequences have a 500ms timeout.
 */
export function KeyboardShortcuts() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams();
  const slug = params?.slug as string | undefined;

  const [showHelp, setShowHelp] = useState(false);
  const [sequenceState, setSequenceState] = useState<SequenceDetectorState>({
    firstKey: null,
    timestamp: 0,
  });

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if modifier keys are pressed (except for Cmd/Ctrl on Mac for ⌘K)
      const isMac = typeof navigator !== "undefined" && navigator.platform.toUpperCase().indexOf("MAC") >= 0;
      const isMetaKey = isMac ? e.metaKey : e.ctrlKey;

      // Skip if target is an input, textarea, or contenteditable
      const target = e.target as HTMLElement;
      const isEditableElement = ["INPUT", "TEXTAREA"].includes(target.tagName) || target.contentEditable === "true";

      // `?` — show help (not in editable)
      if (e.key === "?" && !isEditableElement && !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey) {
        e.preventDefault();
        setShowHelp(true);
        return;
      }

      // `Escape` — close dialogs (always)
      if (e.key === "Escape") {
        // Dispatch custom event for dialogs to listen to
        window.dispatchEvent(new CustomEvent("keyboard:escape"));
        setShowHelp(false);
        return;
      }

      // Skip other shortcuts if in editable element
      if (isEditableElement) return;

      // Skip if Cmd/Ctrl/Alt is pressed (except our special sequences)
      if ((isMetaKey || e.ctrlKey || e.altKey) && e.key !== "c") return;

      // Two-key sequences: `c p`, `c t`, `c i`
      const now = Date.now();
      const timeSinceLastKey = now - sequenceState.timestamp;

      if (e.key.toLowerCase() === "c" && timeSinceLastKey > 500) {
        // Start a new sequence
        e.preventDefault();
        setSequenceState({ firstKey: "c", timestamp: now });
        return;
      }

      if (sequenceState.firstKey === "c" && timeSinceLastKey < 500) {
        // Complete a sequence
        const secondKey = e.key.toLowerCase();

        if (secondKey === "p") {
          // Create project
          e.preventDefault();
          if (slug) {
            router.push(`/org/${slug}/projects?create=true`);
          }
          setSequenceState({ firstKey: null, timestamp: 0 });
          return;
        }

        if (secondKey === "t") {
          // Create task
          e.preventDefault();
          window.dispatchEvent(new CustomEvent("keyboard:create-task"));
          setSequenceState({ firstKey: null, timestamp: 0 });
          return;
        }

        if (secondKey === "i") {
          // Create invoice
          e.preventDefault();
          window.dispatchEvent(new CustomEvent("keyboard:create-invoice"));
          setSequenceState({ firstKey: null, timestamp: 0 });
          return;
        }

        // If second key doesn't match any combo, reset
        setSequenceState({ firstKey: null, timestamp: 0 });
        return;
      }

      // `j` / `k` — navigate table rows
      if ((e.key === "j" || e.key === "k") && !isEditableElement) {
        e.preventDefault();
        const direction = e.key === "j" ? "next" : "prev";
        window.dispatchEvent(new CustomEvent("keyboard:navigate-row", { detail: { direction } }));
        return;
      }

      // `Enter` — open selected row
      if (e.key === "Enter" && !isEditableElement) {
        e.preventDefault();
        window.dispatchEvent(new CustomEvent("keyboard:open-row"));
        return;
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [slug, router, sequenceState]);

  // Sequence timeout cleanup
  useEffect(() => {
    if (sequenceState.firstKey === null) return;

    const timeoutId = setTimeout(() => {
      setSequenceState({ firstKey: null, timestamp: 0 });
    }, 500);

    return () => clearTimeout(timeoutId);
  }, [sequenceState]);

  return <ShortcutHelpDialog open={showHelp} onOpenChange={setShowHelp} />;
}
