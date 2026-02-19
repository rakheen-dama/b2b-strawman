# Claude Slack Bot

A Slack bot that runs Claude Code CLI prompts locally on your Mac. Uses your Max subscription — no API credits.

## Architecture

```
Slack (phone) → Socket Mode → Bolt.js → spawns `claude -p` → your repo
                                              ↑
                                    uses local auth (Max sub),
                                    reads CLAUDE.md, skills,
                                    slash commands, project context
```

- **Per-thread sessions**: Each Slack thread maintains its own Claude conversation via `--resume`.
- **Streaming updates**: Posts a "Working..." message and progressively updates it as Claude streams output.
- **Slash commands**: Type `/review 225` or `/epic 110A` — passed as the prompt text, triggering your configured skills.
- **Stop/Reset**: `/stop` kills the running process, `/reset` clears thread context.

## Prerequisites

- Claude Code CLI installed and authenticated (`claude` command available in your PATH)
- Node.js 20+ and pnpm
- A Slack workspace you can install apps to

## Setup

### 1. Create a Slack App

1. Go to [api.slack.com/apps](https://api.slack.com/apps) → **Create New App** → **From scratch**
2. Name it (e.g. "Claude Code") and pick your workspace
3. **Socket Mode**: Settings → Socket Mode → Enable → generate app-level token with `connections:write` → save as `SLACK_APP_TOKEN`
4. **Bot User**: Features → App Home → toggle Messages Tab on, check "Allow users to send messages"
5. **OAuth Scopes**: `app_mentions:read`, `chat:write`, `im:history`, `im:read`, `im:write`
6. **Event Subscriptions**: Enable → subscribe to `app_mention` + `message.im`
7. **Install to Workspace** → copy Bot User OAuth Token as `SLACK_BOT_TOKEN`

### 2. Environment Variables

```bash
cp .env.example .env
```

Edit `.env`:

```env
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
CLAUDE_CWD=/Users/you/Projects/2026/b2b-strawman
CLAUDE_PERMISSION_MODE=bypassPermissions
CLAUDE_MODEL=sonnet
```

No `ANTHROPIC_API_KEY` needed — the bot spawns `claude` CLI which uses your local auth.

### 3. Install & Run

```bash
pnpm install
pnpm dev        # development (auto-reload)
# or
pnpm build && pnpm start   # production
```

## Usage

### Direct Messages
Open a DM with the bot:
- `What files handle authentication?`
- `/review 225`
- `Explain the TenantFilter class`

### Channel Mentions
In any channel the bot is invited to:
- `@Claude Code What's the current git branch?`

### Thread Context
Reply in the same thread to continue the conversation — Claude remembers the full session.

### Commands
- `/stop` — kill the running Claude process for this thread
- `/reset` — clear this thread's conversation context and start fresh

## Configuration

| Env Var | Default | Description |
|---------|---------|-------------|
| `SLACK_BOT_TOKEN` | (required) | Bot User OAuth Token (xoxb-...) |
| `SLACK_APP_TOKEN` | (required) | App-Level Token (xapp-...) |
| `CLAUDE_CWD` | repo root | Working directory for Claude CLI |
| `CLAUDE_PERMISSION_MODE` | `bypassPermissions` | CLI permission mode |
| `CLAUDE_MODEL` | `sonnet` | Model alias or full ID |
| `CLAUDE_MAX_TURNS` | `50` | Max agent turns per prompt |

## Troubleshooting

- **"Failed to spawn claude CLI"**: Ensure `claude` is in your PATH (`which claude`)
- **"Missing required environment variable"**: Check `.env` has `SLACK_BOT_TOKEN` and `SLACK_APP_TOKEN`
- **Bot doesn't respond to DMs**: Ensure `message.im` event is subscribed and Messages Tab is on
- **Bot doesn't respond to @mentions**: Ensure `app_mention` is subscribed and bot is invited to the channel
- **Process hangs**: Use `/stop` in the thread, or check console for errors
