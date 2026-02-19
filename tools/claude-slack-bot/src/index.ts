import "dotenv/config";
import { loadConfig } from "./config.js";
import { AgentClient } from "./agentClient.js";
import { createSlackApp } from "./slackApp.js";

async function main(): Promise<void> {
  const config = loadConfig();

  console.info("[boot] Claude Slack Bot starting...");
  console.info(`[boot] Agent CWD: ${config.agent.cwd}`);
  console.info(`[boot] Permission mode: ${config.agent.permissionMode}`);
  console.info(`[boot] Model: ${config.agent.model}`);

  const agentClient = new AgentClient(config);
  const app = createSlackApp(config, agentClient);

  await app.start();
  console.info("[boot] Slack bot is running (Socket Mode)");

  // Graceful shutdown
  const shutdown = async (signal: string) => {
    console.info(`[shutdown] Received ${signal}, stopping...`);
    await app.stop();
    console.info(`[shutdown] Active sessions: ${agentClient.sessionCount}`);
    process.exit(0);
  };

  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));
}

main().catch((err) => {
  console.error("[fatal]", err);
  process.exit(1);
});
