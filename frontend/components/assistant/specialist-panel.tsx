"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from "react";
import { Sparkles, Send, Square } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useAssistant } from "@/components/assistant/assistant-provider";
import { useAssistantChat, type ChatMessage } from "@/hooks/use-assistant-chat";
import { UserMessage } from "@/components/assistant/user-message";
import { AssistantMessage } from "@/components/assistant/assistant-message";
import { ToolUseCard } from "@/components/assistant/tool-use-card";
import { ConfirmationCard } from "@/components/assistant/confirmation-card";
import { ToolResultCard } from "@/components/assistant/tool-result-card";
import { ErrorCard } from "@/components/assistant/error-card";
import { TokenUsageBadge } from "@/components/assistant/token-usage-badge";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";
import type { SessionHandle } from "@/lib/api/assistant-specialists";

export interface SpecialistPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sessionHandle: SessionHandle;
  initialPrompt?: string;
  contextRef: { entityType: string; entityId: string };
  /** Optional tagline override (otherwise falls back to a generic string). */
  tagline?: string;
}

/**
 * Truncate the message transcript into a <=500-char summary suitable for
 * pre-seeding the generalist on hand-off (see Phase 70 architecture §3.8).
 */
export function buildHandOffSummary(messages: ChatMessage[], maxLen = 500): string {
  if (messages.length === 0) return "";
  const parts: string[] = [];
  for (const m of messages) {
    if (m.role !== "user" && m.role !== "assistant") continue;
    parts.push(`${m.role}: ${m.content}`);
  }
  const joined = parts.join("\n");
  return joined.length > maxLen ? joined.slice(0, maxLen) : joined;
}

export function SpecialistPanel({
  open,
  onOpenChange,
  sessionHandle,
  initialPrompt,
  contextRef,
  tagline,
}: SpecialistPanelProps) {
  // Reference `contextRef` so the linter doesn't flag it; future epics may use
  // it to render context-aware panel chrome.
  void contextRef;

  const generalist = useAssistant();
  const {
    messages,
    isStreaming,
    tokenUsage,
    sendMessage,
    stopStreaming,
    confirmToolCall,
  } = useAssistantChat({ specialistId: sessionHandle.specialistId });

  const [input, setInput] = useState("");
  const seededRef = useRef(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const toolResultIds = useMemo(
    () =>
      new Set(
        messages
          .filter((m) => m.role === "tool_result")
          .map((m) => m.toolCallId)
          .filter(Boolean) as string[]
      ),
    [messages]
  );

  // Pre-seed: send the initialPrompt as the first user message exactly once.
  useEffect(() => {
    if (!open || seededRef.current) return;
    seededRef.current = true;
    if (initialPrompt && initialPrompt.trim().length > 0) {
      sendMessage(initialPrompt);
    }
  }, [open, initialPrompt, sendMessage]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
  }, [messages]);

  useEffect(() => {
    if (open) {
      const timer = setTimeout(() => textareaRef.current?.focus(), 100);
      return () => clearTimeout(timer);
    }
  }, [open]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || isStreaming) return;
    setInput("");
    sendMessage(trimmed);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleHandOff = () => {
    // Build summary first so we don't lose access after the panel closes.
    const summary = buildHandOffSummary(messages);
    onOpenChange(false);
    if (!generalist.isOpen) {
      generalist.toggle();
    }
    // The generalist's chat hook is a separate instance; re-issue the summary
    // as a fresh user message there. Phase 52 doesn't expose a cross-instance
    // pre-seed bus yet, so consumers wire their own bridging. For now we
    // attach the summary to the global window so the generalist panel can
    // optionally consume it. Future slices may replace this with a shared store.
    if (typeof window !== "undefined" && summary) {
      (window as unknown as { __kaziHandOffSummary?: string }).__kaziHandOffSummary =
        summary;
    }
  };

  // Optionally render a pre-seeded assistant message (from SessionHandle) once.
  const preSeed = sessionHandle.preSeededAssistantMessage;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="flex h-full flex-col sm:max-w-[420px]"
        showCloseButton={false}
      >
        <SheetHeader className="flex-row items-center justify-between border-b border-slate-200 pb-3 dark:border-slate-700">
          <div className="flex items-center gap-2">
            <Sparkles className="size-4 text-teal-600" />
            <div className="flex flex-col">
              <SheetTitle className="text-base">{sessionHandle.displayName}</SheetTitle>
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {tagline ?? SPECIALIST_STRINGS.panelHeaderFallbackTagline}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {(tokenUsage.input > 0 || tokenUsage.output > 0) && (
              <TokenUsageBadge
                inputTokens={tokenUsage.input}
                outputTokens={tokenUsage.output}
              />
            )}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => onOpenChange(false)}
              aria-label={SPECIALIST_STRINGS.closePanel}
            >
              <span className="text-lg leading-none">&times;</span>
            </Button>
          </div>
        </SheetHeader>

        <SheetDescription className="sr-only">
          {SPECIALIST_STRINGS.poweredByAi}
        </SheetDescription>

        <div className="flex-1 overflow-y-auto px-1 py-3">
          <div className="flex flex-col gap-2">
            {preSeed && (
              <AssistantMessage
                key="pre-seed"
                message={{ content: preSeed }}
                isStreaming={false}
              />
            )}
            {messages.map((msg, index) => {
              const isLastMessage = index === messages.length - 1;
              if (msg.role === "user") {
                return <UserMessage key={msg.id} message={msg} />;
              }
              if (msg.role === "assistant") {
                return (
                  <AssistantMessage
                    key={msg.id}
                    message={msg}
                    isStreaming={isStreaming && isLastMessage}
                  />
                );
              }
              if (msg.role === "tool_use") {
                if (msg.requiresConfirmation) {
                  return (
                    <ConfirmationCard
                      key={msg.id}
                      message={msg}
                      onConfirm={confirmToolCall}
                    />
                  );
                }
                return (
                  <ToolUseCard
                    key={msg.id}
                    message={msg}
                    isLoading={
                      msg.toolCallId ? !toolResultIds.has(msg.toolCallId) : false
                    }
                  />
                );
              }
              if (msg.role === "tool_result") {
                return <ToolResultCard key={msg.id} message={msg} />;
              }
              if (msg.role === "error") {
                return <ErrorCard key={msg.id} message={msg} />;
              }
              return null;
            })}
            <div ref={messagesEndRef} />
          </div>
        </div>

        <div className="border-t border-slate-200 px-1 pt-3 dark:border-slate-700">
          <div className="flex gap-2">
            <Textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={SPECIALIST_STRINGS.inputPlaceholder}
              disabled={isStreaming}
              className="min-h-10 resize-none"
              rows={1}
            />
            {isStreaming ? (
              <Button
                variant="outline"
                size="icon"
                onClick={stopStreaming}
                aria-label={SPECIALIST_STRINGS.stopStreaming}
                className="shrink-0"
              >
                <Square className="size-4" />
              </Button>
            ) : (
              <Button
                variant="accent"
                size="icon"
                onClick={handleSend}
                disabled={!input.trim()}
                aria-label={SPECIALIST_STRINGS.sendMessage}
                className="shrink-0"
              >
                <Send className="size-4" />
              </Button>
            )}
          </div>
          <div className="flex items-center justify-between pt-2 pb-1 text-xs">
            <span className="text-slate-400 dark:text-slate-500">
              {SPECIALIST_STRINGS.poweredByAi}
            </span>
            <Button
              variant="plain"
              size="xs"
              onClick={handleHandOff}
              className="text-teal-600 hover:text-teal-700"
            >
              {SPECIALIST_STRINGS.handOffToGeneralist}
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
