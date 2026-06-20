# Docs image — Nextra static export served by Caddy.
# Built from the REPO ROOT context (pnpm workspace member):
#   docker build -f docs/Dockerfile .
# The docs site is pure MDX content, so it ships as static HTML behind a tiny
# caddy:alpine server (~few MB RAM) rather than a Node server. The main edge
# Caddy reverse-proxies docs.heykazi.com -> this container's :80.

# ---- Stage 1: Install deps (workspace-aware, docs subtree only) ----
FROM node:20-alpine AS deps
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

# Root lockfile + workspace manifest drive a deterministic frozen install.
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY docs/package.json ./docs/
# docs has no @b2mash/* workspace deps, so only its own manifest is needed.
RUN pnpm install --frozen-lockfile --filter heykazi-docs...

# ---- Stage 2: Build (Nextra static export + pagefind search index) ----
FROM node:20-alpine AS builder
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/docs/node_modules ./docs/node_modules
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
# docs/tsconfig.json extends ../tsconfig.base.json.
COPY tsconfig.base.json ./
COPY docs/ ./docs/

ENV NEXT_TELEMETRY_DISABLED=1
# `build` = next build (output:export -> docs/out) && pagefind --site out.
RUN pnpm --filter heykazi-docs build

# ---- Stage 3: Static runtime (caddy serving the export on internal :80) ----
FROM caddy:2-alpine AS runner
COPY docs/Caddyfile.static /etc/caddy/Caddyfile
COPY --from=builder /app/docs/out /srv
EXPOSE 80
