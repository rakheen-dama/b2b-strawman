import "dotenv/config";
import { loadConfig } from "./config.js";
import { ClaudeRunner } from "./claudeRunner.js";
import { createSlackApp } from "./slackApp.js";

async function main(): Promise<void> {
  const config = loadConfig();

  console.info("[boot] Claude Slack Bot starting...");
  console.info(`[boot] Claude CWD: ${config.claude.cwd}`);
  console.info(`[boot] Permission mode: ${config.claude.permissionMode}`);
  console.info(`[boot] Model: ${config.claude.model}`);

  const runner = new ClaudeRunner(config);
  const app = createSlackApp(config, runner);

  await app.start();
  console.info("[boot] Slack bot is running (Socket Mode)");

  // Graceful shutdown
  const shutdown = async (signal: string) => {
    console.info(`[shutdown] Received ${signal}, stopping...`);
    runner.abortAll();
    await app.stop();
    console.info(
      `[shutdown] Sessions: ${runner.sessionCount}, Running: ${runner.runningCount}`,
    );
    process.exit(0);
  };

  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));
}

main().catch((err) => {
  console.error("[fatal]", err);
  process.exit(1);
});
