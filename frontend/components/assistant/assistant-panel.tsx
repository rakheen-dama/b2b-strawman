"use client";

import { useRef, useEffect, useState, type KeyboardEvent } from "react";
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
import { cn } from "@/lib/utils";
import { useAssistant } from "@/components/assistant/assistant-provider";
import { useAssistantChat, type ChatMessage } from "@/hooks/use-assistant-chat";

// ---- Message bubble ----

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";
  const isError = message.role === "error";
  const isToolUse = message.role === "tool_use";
  const isToolResult = message.role === "tool_result";

  if (isToolUse) {
    return (
      <div className="my-1 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        <span className="font-medium">Tool:</span> {message.toolName}
        {message.requiresConfirmation && (
          <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-amber-700 dark:bg-amber-900 dark:text-amber-300">
            Needs confirmation
          </span>
        )}
      </div>
    );
  }

  if (isToolResult) {
    return (
      <div
        className={cn(
          "my-1 rounded-md border px-3 py-2 text-xs",
          message.success
            ? "border-teal-200 bg-teal-50 text-teal-700 dark:border-teal-800 dark:bg-teal-900/30 dark:text-teal-400"
            : "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-400",
        )}
      >
        <span className="font-medium">
          {message.toolName} {message.success ? "result" : "failed"}:
        </span>{" "}
        {message.content.length > 200
          ? message.content.slice(0, 200) + "..."
          : message.content}
      </div>
    );
  }

  return (
    <div
      className={cn(
        "max-w-[85%] rounded-lg px-3 py-2 text-sm",
        isUser
          ? "ml-auto bg-teal-600 text-white"
          : isError
            ? "bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-400"
            : "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-100",
      )}
    >
      <p className="whitespace-pre-wrap">{message.content}</p>
    </div>
  );
}

// ---- Panel ----

export function AssistantPanel() {
  const { isOpen, toggle } = useAssistant();
  const { messages, isStreaming, tokenUsage, sendMessage, stopStreaming } =
    useAssistantChat();

  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "instant" });
  }, [messages]);

  // Focus textarea when panel opens
  useEffect(() => {
    if (isOpen) {
      // Small delay for the sheet animation
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
              <span className="text-xs text-slate-400">
                {tokenUsage.input + tokenUsage.output} tokens
              </span>
            )}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={toggle}
              aria-label="Close assistant"
            >
              <span className="text-lg leading-none">&times;</span>
            </Button>
          </div>
        </SheetHeader>

        <SheetDescription className="sr-only">
          Chat with the DocTeams AI assistant
        </SheetDescription>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto px-1 py-3">
          {messages.length === 0 && (
            <div className="flex h-full items-center justify-center">
              <p className="text-sm text-slate-400">
                Ask anything about your workspace...
              </p>
            </div>
          )}
          <div className="flex flex-col gap-2">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}
            <div ref={messagesEndRef} />
          </div>
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
