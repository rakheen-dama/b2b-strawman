import { App } from "@slack/bolt";
import type { Config } from "./config.js";
import type { ClaudeRunner } from "./claudeRunner.js";

const HELP_TEXT = `Hey! I'm your Claude Code bot. Send me a message and I'll run it as a prompt against your repo using your local Claude Code.

*Examples:*
• \`What files handle authentication?\`
• \`/review 225\` — review a PR
• \`/breakdown 16\` — break down a phase
• \`Explain the TenantFilter class\`
• \`/stop\` — kill the running Claude process for this thread
• \`/reset\` — clear this thread's conversation context

Uses your Max subscription — no API credits.`;

const GREETING_PATTERNS = /^(hi|hello|hey|sup|yo|howdy|greetings|help|\?)$/i;

/** Throttled Slack message updater — avoids rate limits by batching updates. */
class MessageUpdater {
  private pending: string | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private lastUpdate = 0;

  constructor(
    private doUpdate: (text: string) => Promise<void>,
    private intervalMs = 1500,
  ) {}

  schedule(text: string): void {
    this.pending = text;
    if (this.timer) return;

    const elapsed = Date.now() - this.lastUpdate;
    const delay = Math.max(0, this.intervalMs - elapsed);

    this.timer = setTimeout(() => {
      this.timer = null;
      if (this.pending) {
        const t = this.pending;
        this.pending = null;
        this.lastUpdate = Date.now();
        this.doUpdate(t).catch(() => {});
      }
    }, delay);
  }

  async flush(text: string): Promise<void> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.pending = null;
    await this.doUpdate(text);
  }
}

export function createSlackApp(config: Config, runner: ClaudeRunner): App {
  const app = new App({
    token: config.slack.botToken,
    socketMode: true,
    appToken: config.slack.appToken,
  });

  /** Handle an incoming prompt (from DM or app_mention). */
  async function handlePrompt(
    text: string,
    channelId: string,
    threadTs: string,
    userId: string,
    client: App["client"],
  ): Promise<void> {
    const trimmed = text.trim().toLowerCase();

    // /stop kills the running process
    if (trimmed === "/stop") {
      const stopped = runner.abort(threadTs);
      await client.chat.postMessage({
        channel: channelId,
        thread_ts: threadTs,
        text: stopped
          ? "Stopped the running Claude process."
          : "No running process for this thread.",
      });
      return;
    }

    // /reset clears the thread's session
    if (trimmed === "/reset") {
      const cleared = runner.clearSession(threadTs);
      await client.chat.postMessage({
        channel: channelId,
        thread_ts: threadTs,
        text: cleared
          ? "Session cleared. Next message starts a fresh conversation."
          : "No active session for this thread.",
      });
      return;
    }

    // Greetings / help
    if (!text.trim() || GREETING_PATTERNS.test(text.trim())) {
      await client.chat.postMessage({
        channel: channelId,
        thread_ts: threadTs,
        text: HELP_TEXT,
      });
      return;
    }

    // Post initial "thinking" message
    const thinkingMsg = await client.chat.postMessage({
      channel: channelId,
      thread_ts: threadTs,
      text: ":hourglass_flowing_sand: Working on it...",
    });

    const messageTs = thinkingMsg.ts;
    if (!messageTs) {
      console.error("[slack] Failed to post thinking message — no ts returned");
      return;
    }

    const updater = new MessageUpdater(async (content: string) => {
      const maxLen = 39_000;
      const truncated =
        content.length > maxLen
          ? content.slice(0, maxLen) + "\n\n_(response truncated)_"
          : content;

      await client.chat.update({
        channel: channelId,
        ts: messageTs,
        text: truncated,
      });
    });

    try {
      console.info(
        `[claude] Running prompt for user=${userId} channel=${channelId} thread=${threadTs}`,
      );

      const response = await runner.runPrompt({
        threadId: threadTs,
        text,
        onChunk(accumulated) {
          updater.schedule(accumulated);
        },
      });

      await updater.flush(response);

      console.info(
        `[claude] Completed for user=${userId} channel=${channelId} thread=${threadTs} (${response.length} chars)`,
      );
    } catch (error) {
      const errMsg =
        error instanceof Error ? error.message : "Unknown error occurred";
      console.error(
        `[claude] Error for user=${userId} channel=${channelId}: ${errMsg}`,
      );

      await updater.flush(
        `:x: Something went wrong.\n\`\`\`${errMsg}\`\`\``,
      );
    }
  }

  // --- Event listeners ---

  // Direct messages
  app.message(async ({ message, client }) => {
    if (!("text" in message) || !message.text) return;
    if ("subtype" in message && message.subtype) return;
    if (!("user" in message) || !message.user) return;

    const threadTs =
      "thread_ts" in message && message.thread_ts
        ? message.thread_ts
        : message.ts;
    await handlePrompt(
      message.text,
      message.channel,
      threadTs,
      message.user,
      client,
    );
  });

  // @mentions in channels
  app.event("app_mention", async ({ event, client }) => {
    const text = event.text.replace(/<@[A-Z0-9]+>\s*/g, "").trim();
    const threadTs = event.thread_ts ?? event.ts;
    if (!event.user) return;
    await handlePrompt(text, event.channel, threadTs, event.user, client);
  });

  return app;
}
