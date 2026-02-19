import { spawn, type ChildProcess } from "node:child_process";
import type { Config } from "./config.js";

/** Callback invoked with incremental text as Claude streams its response. */
export type OnTextChunk = (accumulated: string) => void;

interface RunPromptParams {
  threadId: string;
  text: string;
  onChunk?: OnTextChunk;
}

/**
 * Runs Claude Code CLI as a child process and streams output back.
 *
 * - First message in a thread → new `claude -p` session.
 * - Follow-up messages → `claude -p --resume <session-id>` to continue the conversation.
 * - Sessions are tracked per Slack thread (in-memory, lost on restart).
 * - Uses your local Claude Code auth (Max subscription) — no API key needed.
 */
export class ClaudeRunner {
  /** Slack thread_ts → Claude session_id */
  private sessions = new Map<string, string>();

  /** Currently running child processes (for graceful shutdown / abort). */
  private running = new Map<string, ChildProcess>();

  constructor(private config: Config) {}

  async runPrompt({
    threadId,
    text,
    onChunk,
  }: RunPromptParams): Promise<string> {
    const existingSessionId = this.sessions.get(threadId);

    const args = [
      "-p",
      text,
      "--output-format",
      "stream-json",
      "--verbose",
      "--model",
      this.config.claude.model,
      "--permission-mode",
      this.config.claude.permissionMode,
      "--max-turns",
      String(this.config.claude.maxTurns),
    ];

    if (existingSessionId) {
      args.push("--resume", existingSessionId);
    }

    return new Promise<string>((resolve, reject) => {
      const child = spawn("claude", args, {
        cwd: this.config.claude.cwd,
        env: { ...process.env },
        stdio: ["pipe", "pipe", "pipe"],
      });

      this.running.set(threadId, child);

      let accumulated = "";
      let sessionId: string | undefined;
      let buffer = "";

      child.stdout.on("data", (data: Buffer) => {
        buffer += data.toString();

        // Process complete JSON lines
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? ""; // keep incomplete last line in buffer

        for (const line of lines) {
          if (!line.trim()) continue;
          try {
            const event = JSON.parse(line);

            // Capture session ID from init message
            if (event.type === "system" && event.subtype === "init" && event.session_id) {
              sessionId = event.session_id;
            }

            // Extract streaming text deltas
            if (
              event.type === "assistant" &&
              event.message?.content
            ) {
              for (const block of event.message.content) {
                if (block.type === "text" && block.text) {
                  accumulated += block.text;
                  onChunk?.(accumulated);
                }
              }
            }

            // Also capture result message (final output)
            if (event.type === "result" && event.result) {
              // Result is the final text — use it if we haven't accumulated anything
              if (!accumulated) {
                accumulated = event.result;
                onChunk?.(accumulated);
              }
              if (event.session_id) {
                sessionId = event.session_id;
              }
            }
          } catch {
            // Not valid JSON — skip (could be partial line or debug output)
          }
        }
      });

      let stderr = "";
      child.stderr.on("data", (data: Buffer) => {
        stderr += data.toString();
      });

      child.on("close", (code) => {
        this.running.delete(threadId);

        // Persist session for thread continuity
        if (sessionId) {
          this.sessions.set(threadId, sessionId);
        }

        if (code !== 0 && !accumulated) {
          reject(
            new Error(
              `Claude CLI exited with code ${code}${stderr ? `: ${stderr.slice(0, 500)}` : ""}`,
            ),
          );
          return;
        }

        resolve(accumulated || "(No response from Claude)");
      });

      child.on("error", (err) => {
        this.running.delete(threadId);
        reject(new Error(`Failed to spawn claude CLI: ${err.message}`));
      });

      // Close stdin — we pass the prompt via args, not stdin
      child.stdin.end();
    });
  }

  /** Kill a running Claude process for a thread. */
  abort(threadId: string): boolean {
    const child = this.running.get(threadId);
    if (child) {
      child.kill("SIGINT");
      this.running.delete(threadId);
      return true;
    }
    return false;
  }

  /** Remove a thread's session (e.g. on explicit /reset command). */
  clearSession(threadId: string): boolean {
    this.abort(threadId);
    return this.sessions.delete(threadId);
  }

  /** Number of active sessions (for diagnostics). */
  get sessionCount(): number {
    return this.sessions.size;
  }

  /** Number of currently running processes. */
  get runningCount(): number {
    return this.running.size;
  }

  /** Kill all running processes (for graceful shutdown). */
  abortAll(): void {
    for (const [threadId, child] of this.running) {
      child.kill("SIGINT");
      this.running.delete(threadId);
    }
  }
}
