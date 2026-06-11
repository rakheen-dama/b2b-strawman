# Portal Docker image — built from the REPO ROOT context (pnpm workspace member).
# The portal joined the pnpm workspace, so the lockfile + workspace manifest live
# at the repo root. Build with: `docker build -f portal/Dockerfile .` from the root.

# ---- Stage 1: Install dependencies (workspace-aware, portal subtree only) ----
FROM node:20-alpine AS deps
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

# Workspace manifests + root lockfile drive a deterministic, frozen install.
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY portal/package.json ./portal/
# Workspace dependencies (@b2mash/ui, @b2mash/shared) — their manifests are part
# of the install graph, so the frozen install can resolve the workspace links.
COPY packages/ui/package.json ./packages/ui/
COPY packages/shared/package.json ./packages/shared/

# Install only the portal package and its dependency closure from the root lockfile.
RUN pnpm install --frozen-lockfile --filter portal...

# ---- Stage 2: Build ----
FROM node:20-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.26.1 --activate

# Bring in the installed workspace node_modules from the deps stage.
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/portal/node_modules ./portal/node_modules
# @b2mash/ui's own dependency tree (pnpm may place deps under the package).
COPY --from=deps /app/packages ./packages

# Workspace manifests (so pnpm resolves the `portal` filter) + portal source.
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
# Shared base tsconfig — portal/tsconfig.json extends ../tsconfig.base.json.
COPY tsconfig.base.json ./
COPY portal/ ./portal/
# @b2mash/ui ships TS source (no build step) — transpilePackages compiles it
# during the portal build, so its sources must be present.
COPY packages/ ./packages/

# NEXT_PUBLIC_ vars are inlined at build time. Pass via --build-arg.
ARG NEXT_PUBLIC_PORTAL_API_URL
ENV NEXT_PUBLIC_PORTAL_API_URL=$NEXT_PUBLIC_PORTAL_API_URL
ENV NEXT_TELEMETRY_DISABLED=1

RUN pnpm --filter portal build

# ---- Stage 3: Production runner ----
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3001

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

RUN apk add --no-cache curl

# Next standalone output under a pnpm workspace traces the workspace root.
# The build context root maps to /app, so the standalone emits:
#   /app/portal/server.js  (entrypoint)
#   /app/node_modules      (hoisted workspace deps)
# We copy the whole standalone tree to /app to preserve those relative paths.
COPY --from=builder --chown=nextjs:nodejs /app/portal/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/portal/.next/static ./portal/.next/static
COPY --from=builder --chown=nextjs:nodejs /app/portal/public ./portal/public

USER nextjs

# Healthcheck honours the PORT override (compose sets PORT=3002).
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s \
  CMD curl -f http://localhost:${PORT}/ || exit 1

EXPOSE 3001

CMD ["node", "portal/server.js"]
