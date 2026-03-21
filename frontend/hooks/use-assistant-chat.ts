"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { parseSseEvents } from "@/lib/sse-parser";

// ---- Types ----

export type ChatMessageRole = "user" | "assistant" | "tool_use" | "tool_result" | "error";

export interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  content: string;
  toolCallId?: string;
  toolName?: string;
  requiresConfirmation?: boolean;
  success?: boolean;
}

export interface TokenUsage {
  input: number;
  output: number;
}

// ---- Auth helpers ----
// API_BASE uses NEXT_PUBLIC_BACKEND_URL (same env var as portal-api.ts and hooks.ts)
// to reach the backend in mock/e2e mode; in keycloak mode, relative URLs are used.

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const API_BASE =
  AUTH_MODE === "keycloak"
    ? "" // relative URL — SESSION cookie forwarded automatically
    : process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (AUTH_MODE === "mock") {
    const match = document.cookie.match(/(?:^|;\s*)mock-auth-token=([^;]+)/);
    if (match) {
      headers["Authorization"] = `Bearer ${decodeURIComponent(match[1])}`;
    }
  }
  if (AUTH_MODE === "keycloak") {
    const xsrf = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (xsrf) {
      headers["X-XSRF-TOKEN"] = decodeURIComponent(xsrf[1]);
    }
  }
  return headers;
}

// ---- Helpers ----

function nextId(): string {
  return `msg_${crypto.randomUUID()}`;
}

function buildHistory(
  messages: ChatMessage[],
): { role: string; content: string }[] {
  return messages
    .filter((m) => m.role === "user" || m.role === "assistant")
    .map((m) => ({ role: m.role, content: m.content }));
}

// ---- Hook ----

export function useAssistantChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [tokenUsage, setTokenUsage] = useState<TokenUsage>({
    input: 0,
    output: 0,
  });

  const abortControllerRef = useRef<AbortController | null>(null);
  const streamingMessageIdRef = useRef<string | null>(null);
  const messagesRef = useRef<ChatMessage[]>(messages);
  const isStreamingRef = useRef(false);

  // Keep refs in sync with state for use inside async callbacks
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || isStreamingRef.current) return;

      // Set streaming ref synchronously to prevent double-sends
      isStreamingRef.current = true;

      // Add user message
      const userMsg: ChatMessage = {
        id: nextId(),
        role: "user",
        content: trimmed,
      };

      const assistantMsgId = nextId();
      streamingMessageIdRef.current = assistantMsgId;

      // Add user message and an empty assistant message to stream into
      setMessages((prev) => [
        ...prev,
        userMsg,
        { id: assistantMsgId, role: "assistant", content: "" },
      ]);
      setIsStreaming(true);

      const controller = new AbortController();
      abortControllerRef.current = controller;

      try {
        const currentMessages = [
          ...messagesRef.current,
          userMsg,
        ];

        const response = await fetch(`${API_BASE}/api/assistant/chat`, {
          method: "POST",
          headers: getAuthHeaders(),
          credentials: AUTH_MODE === "keycloak" ? "include" : "omit",
          body: JSON.stringify({
            message: trimmed,
            history: buildHistory(currentMessages),
            currentPage:
              typeof window !== "undefined" ? window.location.pathname : "",
          }),
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId
                ? {
                    ...m,
                    role: "error" as const,
                    content: `Request failed: ${response.status} ${response.statusText}`,
                  }
                : m,
            ),
          );
          setIsStreaming(false);
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let sseBuffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          sseBuffer += decoder.decode(value, { stream: true });
          const { parsed, remainder } = parseSseEvents(sseBuffer);
          sseBuffer = remainder;

          for (const event of parsed) {
            switch (event.type) {
              case "text_delta": {
                const delta = event.data as { text: string };
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === assistantMsgId
                      ? { ...m, content: m.content + delta.text }
                      : m,
                  ),
                );
                break;
              }
              case "tool_use": {
                const toolData = event.data as {
                  toolCallId: string;
                  toolName: string;
                  input: unknown;
                  requiresConfirmation: boolean;
                };
                setMessages((prev) => [
                  ...prev,
                  {
                    id: nextId(),
                    role: "tool_use",
                    content: JSON.stringify(toolData.input),
                    toolCallId: toolData.toolCallId,
                    toolName: toolData.toolName,
                    requiresConfirmation: toolData.requiresConfirmation,
                  },
                ]);
                break;
              }
              case "tool_result": {
                const resultData = event.data as {
                  toolCallId: string;
                  toolName: string;
                  result: unknown;
                  success: boolean;
                };
                setMessages((prev) => [
                  ...prev,
                  {
                    id: nextId(),
                    role: "tool_result",
                    content:
                      typeof resultData.result === "string"
                        ? resultData.result
                        : JSON.stringify(resultData.result),
                    toolCallId: resultData.toolCallId,
                    toolName: resultData.toolName,
                    success: resultData.success,
                  },
                ]);
                break;
              }
              case "usage": {
                const usage = event.data as {
                  inputTokens: number;
                  outputTokens: number;
                };
                setTokenUsage({
                  input: usage.inputTokens,
                  output: usage.outputTokens,
                });
                break;
              }
              case "done": {
                const doneData = event.data as {
                  totalInputTokens?: number;
                  totalOutputTokens?: number;
                };
                if (doneData.totalInputTokens != null) {
                  setTokenUsage({
                    input: doneData.totalInputTokens,
                    output: doneData.totalOutputTokens ?? 0,
                  });
                }
                setIsStreaming(false);
                break;
              }
              case "error": {
                const errData = event.data as { message: string };
                setMessages((prev) => [
                  ...prev,
                  {
                    id: nextId(),
                    role: "error",
                    content: errData.message,
                  },
                ]);
                setIsStreaming(false);
                break;
              }
            }
          }
        }
      } catch (err) {
        if ((err as Error).name !== "AbortError") {
          setMessages((prev) => [
            ...prev,
            {
              id: nextId(),
              role: "error",
              content:
                err instanceof Error ? err.message : "An error occurred",
            },
          ]);
        }
      } finally {
        isStreamingRef.current = false;
        setIsStreaming(false);
        abortControllerRef.current = null;
        streamingMessageIdRef.current = null;
      }
    },
    [],
  );

  const stopStreaming = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    isStreamingRef.current = false;
    setIsStreaming(false);
  }, []);

  const confirmToolCall = useCallback(
    async (toolCallId: string, approved: boolean) => {
      try {
        await fetch(`${API_BASE}/api/assistant/chat/confirm`, {
          method: "POST",
          headers: getAuthHeaders(),
          credentials: AUTH_MODE === "keycloak" ? "include" : "omit",
          body: JSON.stringify({ toolCallId, approved }),
        });
      } catch (err) {
        setMessages((prev) => [
          ...prev,
          {
            id: nextId(),
            role: "error",
            content:
              err instanceof Error
                ? err.message
                : "Failed to confirm tool call",
          },
        ]);
      }
    },
    [],
  );

  return {
    messages,
    isStreaming,
    tokenUsage,
    sendMessage,
    stopStreaming,
    confirmToolCall,
  };
}
