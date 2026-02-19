import { resolve } from "node:path";
import type { PermissionMode } from "@anthropic-ai/claude-agent-sdk";

export interface Config {
  slack: {
    botToken: string;
    appToken: string;
  };
  agent: {
    apiKey: string;
    cwd: string;
    permissionMode: PermissionMode;
    model: string;
    maxTurns: number;
  };
}

/** Maps user-friendly env var values to SDK PermissionMode values. */
const PERMISSION_MODE_MAP: Record<string, PermissionMode> = {
  plan: "plan",
  acceptEdits: "acceptEdits",
  default: "default",
  full: "bypassPermissions",
  bypassPermissions: "bypassPermissions",
  dontAsk: "dontAsk",
};

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function loadConfig(): Config {
  // Resolve the repo root: 3 levels up from src/ → tools/claude-slack-bot → tools → repo root
  const defaultCwd = resolve(import.meta.dirname, "..", "..", "..");

  const rawMode = process.env.AGENT_PERMISSION_MODE ?? "bypassPermissions";
  const permissionMode = PERMISSION_MODE_MAP[rawMode];
  if (!permissionMode) {
    throw new Error(
      `Invalid AGENT_PERMISSION_MODE: ${rawMode}. Must be one of: ${Object.keys(PERMISSION_MODE_MAP).join(", ")}`,
    );
  }

  return {
    slack: {
      botToken: requireEnv("SLACK_BOT_TOKEN"),
      appToken: requireEnv("SLACK_APP_TOKEN"),
    },
    agent: {
      apiKey: requireEnv("ANTHROPIC_API_KEY"),
      cwd: process.env.AGENT_CWD ?? defaultCwd,
      permissionMode,
      model: process.env.AGENT_MODEL ?? "claude-sonnet-4-6",
      maxTurns: parseInt(process.env.AGENT_MAX_TURNS ?? "50", 10),
    },
  };
}
