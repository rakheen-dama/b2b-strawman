import { resolve } from "node:path";

export type PermissionMode =
  | "default"
  | "acceptEdits"
  | "bypassPermissions"
  | "plan"
  | "dontAsk";

export interface Config {
  slack: {
    botToken: string;
    appToken: string;
  };
  claude: {
    cwd: string;
    permissionMode: PermissionMode;
    model: string;
    maxTurns: number;
  };
}

const VALID_PERMISSION_MODES: PermissionMode[] = [
  "default",
  "acceptEdits",
  "bypassPermissions",
  "plan",
  "dontAsk",
];

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

  const permissionMode = (process.env.CLAUDE_PERMISSION_MODE ??
    "bypassPermissions") as PermissionMode;
  if (!VALID_PERMISSION_MODES.includes(permissionMode)) {
    throw new Error(
      `Invalid CLAUDE_PERMISSION_MODE: ${permissionMode}. Must be one of: ${VALID_PERMISSION_MODES.join(", ")}`,
    );
  }

  return {
    slack: {
      botToken: requireEnv("SLACK_BOT_TOKEN"),
      appToken: requireEnv("SLACK_APP_TOKEN"),
    },
    claude: {
      cwd: process.env.CLAUDE_CWD ?? defaultCwd,
      permissionMode,
      model: process.env.CLAUDE_MODEL ?? "sonnet",
      maxTurns: parseInt(process.env.CLAUDE_MAX_TURNS ?? "50", 10),
    },
  };
}
