# Frontend Docker image — built from the REPO ROOT context (pnpm workspace member).
# The frontend joined the pnpm workspace, so the lockfile + workspace manifest live
# at the repo root. Build with: `docker build -f frontend/Dockerfile .` from the root.

# ---- Stage 1: Install dependencies (workspace-aware, frontend subtree only) ----
FROM node:20-alpine AS deps
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

# Workspace manifests + root lockfile drive a deterministic, frozen install.
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY frontend/package.json ./frontend/

# Install only the frontend package and its dependency closure from the root lockfile.
RUN pnpm install --frozen-lockfile --filter frontend...

# ---- Stage 2: Build ----
FROM node:20-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

# Bring in the installed workspace node_modules from the deps stage.
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/frontend/node_modules ./frontend/node_modules

# Workspace manifests (so pnpm resolves the `frontend` filter) + frontend source.
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY frontend/ ./frontend/

# NEXT_PUBLIC_ vars are inlined at build time. Pass via --build-arg or
# provide a placeholder so the build succeeds (real values at runtime
# won't override these — rebuild with correct keys for production).
ARG NEXT_PUBLIC_AUTH_MODE
ARG NEXT_PUBLIC_MOCK_IDP_URL
ARG NEXT_PUBLIC_BACKEND_URL
ARG NEXT_PUBLIC_GATEWAY_URL
ENV NEXT_PUBLIC_AUTH_MODE=$NEXT_PUBLIC_AUTH_MODE
ENV NEXT_PUBLIC_MOCK_IDP_URL=$NEXT_PUBLIC_MOCK_IDP_URL
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL
ENV NEXT_PUBLIC_GATEWAY_URL=$NEXT_PUBLIC_GATEWAY_URL
ENV NEXT_TELEMETRY_DISABLED=1

RUN pnpm --filter frontend build

# ---- Stage 3: Production runner ----
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

RUN apk add --no-cache curl

# Next standalone output under a pnpm workspace traces the workspace root.
# The build context root maps to /app, so the standalone emits:
#   /app/frontend/server.js  (entrypoint)
#   /app/node_modules        (hoisted workspace deps)
# We copy the whole standalone tree to /app to preserve those relative paths.
COPY --from=builder --chown=nextjs:nodejs /app/frontend/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/frontend/.next/static ./frontend/.next/static
COPY --from=builder --chown=nextjs:nodejs /app/frontend/public ./frontend/public

USER nextjs

# Healthcheck honours the PORT override (compose maps host 3001 -> container 3000).
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s \
  CMD curl -f http://localhost:${PORT}/ || exit 1

EXPOSE 3000

CMD ["node", "frontend/server.js"]
