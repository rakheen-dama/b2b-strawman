# Claude Slack Bot

A Slack bot that forwards messages to Claude Code agent sessions tied to this repo. Run it on your Mac and control your dev environment from Slack on your phone.

## Architecture

```
Slack (phone) → Socket Mode → Bolt.js → Agent SDK query() → Claude Code → repo
                                                    ↑
                                         reads CLAUDE.md, skills,
                                         slash commands, project context
```

- **Per-thread sessions**: Each Slack thread maintains its own agent conversation. Follow-ups have full context.
- **Streaming updates**: The bot posts a "Working..." message and progressively updates it as the agent responds.
- **Slash commands**: Type `/review 225` or `/epic 110A` — the text is passed through to the agent, which triggers your configured skills.

## Setup

### 1. Create a Slack App

1. Go to [api.slack.com/apps](https://api.slack.com/apps) → **Create New App** → **From scratch**
2. Name it (e.g. "Claude Code") and pick your workspace

3. **Enable Socket Mode**:
   - Settings → Socket Mode → **Enable**
   - Generate an app-level token with `connections:write` scope → save as `SLACK_APP_TOKEN`

4. **Add Bot User**:
   - Features → App Home → toggle **Messages Tab** on
   - Check "Allow users to send Slash commands and messages from the messages tab"

5. **OAuth & Permissions** → Bot Token Scopes:
   - `app_mentions:read`
   - `chat:write`
   - `im:history`
   - `im:read`
   - `im:write`

6. **Event Subscriptions** → Enable Events → Subscribe to bot events:
   - `app_mention`
   - `message.im`

7. **Install to Workspace** → copy the **Bot User OAuth Token** as `SLACK_BOT_TOKEN`

### 2. Environment Variables

```bash
cp .env.example .env
```

Edit `.env` with your real values:

```env
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
ANTHROPIC_API_KEY=sk-ant-...
AGENT_CWD=/Users/you/Projects/2026/b2b-strawman
AGENT_PERMISSION_MODE=acceptEdits
AGENT_MODEL=claude-sonnet-4-6
```

### 3. Install & Run

```bash
# Install dependencies
pnpm install

# Development (auto-reload on changes)
pnpm dev

# Production
pnpm build
pnpm start
```

## Usage

### Direct Messages
Open a DM with the bot and type:
- `What files handle authentication?`
- `/review 225`
- `Explain the TenantFilter class`

### Channel Mentions
In any channel the bot is in:
- `@Claude Code What's the current git branch?`

### Thread Context
Reply in the same thread to continue the conversation — the agent remembers everything it did.

### Reset Session
Type `/reset` in a thread to clear the conversation context and start fresh.

## Permission Modes

| Mode | Behavior | Use Case |
|------|----------|----------|
| `plan` | Read-only exploration | Safe browsing from phone |
| `acceptEdits` | Auto-approves file reads/writes | Default — good for reviews, exploration |
| `full` | Auto-approves everything incl. bash | Running `/epic`, `/phase`, deployments |

Change via the `AGENT_PERMISSION_MODE` env var. Restart the bot after changing.

## Troubleshooting

- **"Missing required environment variable"**: Check your `.env` file exists and has all required values
- **Bot doesn't respond to DMs**: Ensure `message.im` event subscription is enabled and the Messages Tab is toggled on
- **Bot doesn't respond to @mentions**: Ensure `app_mention` event subscription is enabled and the bot is invited to the channel
- **Agent errors**: Check the console logs. Common issues: invalid API key, incorrect `AGENT_CWD` path
