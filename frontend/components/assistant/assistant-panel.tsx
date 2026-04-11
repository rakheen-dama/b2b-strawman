"use client";

import { useRef, useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { Sparkles, Send, Square } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useAssistant } from "@/components/assistant/assistant-provider";
import { useAssistantChat } from "@/hooks/use-assistant-chat";
import { UserMessage } from "@/components/assistant/user-message";
import { AssistantMessage } from "@/components/assistant/assistant-message";
import { ToolUseCard } from "@/components/assistant/tool-use-card";
import { ConfirmationCard } from "@/components/assistant/confirmation-card";
import { ToolResultCard } from "@/components/assistant/tool-result-card";
import { ErrorCard } from "@/components/assistant/error-card";
import { TokenUsageBadge } from "@/components/assistant/token-usage-badge";
import { EmptyState } from "@/components/assistant/empty-state";

interface AssistantPanelProps {
  slug: string;
  orgRole: string;
}

export function AssistantPanel({ slug, orgRole }: AssistantPanelProps) {
  const { isOpen, toggle } = useAssistant();
  const { messages, isStreaming, tokenUsage, sendMessage, stopStreaming, confirmToolCall } =
    useAssistantChat();

  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Pre-compute set of resolved tool call IDs (for ToolUseCard isLoading logic)
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

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
  }, [messages]);

  useEffect(() => {
    if (isOpen) {
      const timer = setTimeout(() => textareaRef.current?.focus(), 100);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

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

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && toggle()}>
      <SheetContent
        side="right"
        className="flex h-full flex-col sm:max-w-[420px]"
        showCloseButton={false}
      >
        {/* Header */}
        <SheetHeader className="flex-row items-center justify-between border-b border-slate-200 pb-3 dark:border-slate-700">
          <div className="flex items-center gap-2">
            <Sparkles className="size-4 text-teal-600" />
            <SheetTitle className="text-base">DocTeams Assistant</SheetTitle>
          </div>
          <div className="flex items-center gap-2">
            {(tokenUsage.input > 0 || tokenUsage.output > 0) && (
              <TokenUsageBadge inputTokens={tokenUsage.input} outputTokens={tokenUsage.output} />
            )}
            <Button variant="ghost" size="icon-sm" onClick={toggle} aria-label="Close assistant">
              <span className="text-lg leading-none">&times;</span>
            </Button>
          </div>
        </SheetHeader>

        <SheetDescription className="sr-only">Chat with the DocTeams AI assistant</SheetDescription>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto px-1 py-3">
          {messages.length === 0 ? (
            <EmptyState orgRole={orgRole} slug={slug} />
          ) : (
            <div className="flex flex-col gap-2">
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
                      <ConfirmationCard key={msg.id} message={msg} onConfirm={confirmToolCall} />
                    );
                  }
                  return (
                    <ToolUseCard
                      key={msg.id}
                      message={msg}
                      isLoading={msg.toolCallId ? !toolResultIds.has(msg.toolCallId) : false}
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
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-slate-200 px-1 pt-3 dark:border-slate-700">
          <div className="flex gap-2">
            <Textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Type a message..."
              disabled={isStreaming}
              className="min-h-10 resize-none"
              rows={1}
            />
            {isStreaming ? (
              <Button
                variant="outline"
                size="icon"
                onClick={stopStreaming}
                aria-label="Stop streaming"
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
                aria-label="Send message"
                className="shrink-0"
              >
                <Send className="size-4" />
              </Button>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
