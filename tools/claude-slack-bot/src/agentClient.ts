import {
  query,
  type SDKMessage,
  type SettingSource,
} from "@anthropic-ai/claude-agent-sdk";
import type { Config } from "./config.js";

/** Callback invoked with incremental text as the agent streams its response. */
export type OnTextChunk = (accumulated: string) => void;

interface RunPromptParams {
  threadId: string;
  text: string;
  onChunk?: OnTextChunk;
}

/**
 * Manages Claude agent sessions keyed by Slack thread.
 *
 * - First message in a thread → new session with full config (cwd, skills, tools).
 * - Follow-up messages → resume the existing session so the agent has conversation context.
 * - Sessions live in memory; they're lost on process restart (acceptable for a dev bot).
 */
export class AgentClient {
  /** Slack thread_ts → Agent SDK session_id */
  private sessions = new Map<string, string>();

  constructor(private config: Config) {}

  async runPrompt({
    threadId,
    text,
    onChunk,
  }: RunPromptParams): Promise<string> {
    const existingSessionId = this.sessions.get(threadId);
    let accumulated = "";
    let sessionId: string | undefined;

    const options = existingSessionId
      ? { resume: existingSessionId }
      : {
          cwd: this.config.agent.cwd,
          model: this.config.agent.model,
          permissionMode: this.config.agent.permissionMode,
          maxTurns: this.config.agent.maxTurns,
          settingSources: ["user", "project"] as SettingSource[],
          allowedTools: [
            "Skill",
            "Read",
            "Write",
            "Edit",
            "Bash",
            "Glob",
            "Grep",
            "Task",
            "WebFetch",
            "WebSearch",
          ],
        };

    const stream = query({ prompt: text, options });

    for await (const message of stream) {
      // Capture session ID from any message
      if ("session_id" in message && message.session_id && !sessionId) {
        sessionId = message.session_id as string;
      }

      // Extract text from assistant messages
      if (message.type === "assistant") {
        const assistantMsg = message as SDKMessage & {
          message: {
            content: Array<{ type: string; text?: string }>;
          };
        };
        const textBlocks = assistantMsg.message.content
          .filter(
            (block: { type: string; text?: string }) => block.type === "text",
          )
          .map((block: { type: string; text?: string }) => block.text ?? "")
          .join("");

        if (textBlocks) {
          accumulated += textBlocks;
          onChunk?.(accumulated);
        }
      }
    }

    // Persist session for thread continuity
    if (sessionId) {
      this.sessions.set(threadId, sessionId);
    }

    return accumulated || "(No response from agent)";
  }

  /** Remove a thread's session (e.g. on explicit /reset command). */
  clearSession(threadId: string): boolean {
    return this.sessions.delete(threadId);
  }

  /** Number of active sessions (for diagnostics). */
  get sessionCount(): number {
    return this.sessions.size;
  }
}
