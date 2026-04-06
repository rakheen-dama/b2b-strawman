# AGENTS.md

## Purpose

This repository is a multi-tenant B2B SaaS starter called **DocTeams**. It combines:

- `frontend/`: Next.js 16, React 19, TypeScript 5, Tailwind CSS v4
- `backend/`: Spring Boot 4, Java 25, Maven
- `compose/`: local infra for Postgres and LocalStack
- `infra/`: Terraform

Use this file as the default working guide for the whole repository. Prefer the more specific guide when working inside `frontend/` or `backend/`.

## First Checks

Before making changes:

1. Read `README.md` for startup flow and system shape.
2. If working in `frontend/`, read `frontend/CLAUDE.md`.
3. If working in `backend/`, read `backend/CLAUDE.md`.
4. Check for any more deeply nested `AGENTS.md` files before editing in a subdirectory.

## Repo Rules

- Keep changes focused. Do not refactor unrelated areas.
- Match existing patterns and naming before introducing new structure.
- Fix root causes rather than layering temporary patches when practical.
- Update nearby docs when behavior, setup, or architecture changes.
- Do not add new dependencies unless the task clearly requires them.

## Validation

Use the narrowest validation that proves the change:

- Frontend: run the relevant `pnpm` command from `frontend/`
- Backend: run the relevant Maven command from `backend/`
- Compose changes: validate with the relevant `docker compose` command from `compose/`

Prefer targeted tests or lint checks over full-suite runs unless the change is broad.

## Frontend Notes

- App uses Next.js App Router.
- Preserve the existing design system and component patterns.
- Reuse existing UI components before adding new primitives.
- Keep server/client boundaries explicit.

See `frontend/CLAUDE.md` for detailed frontend conventions.

## Backend Notes

- Keep controllers thin; business logic belongs in services.
- Preserve multitenant isolation assumptions and avoid shortcuts around tenant resolution.
- Follow existing Flyway migration layout and naming.
- Treat auth and capability checks as security-sensitive code.

See `backend/CLAUDE.md` for detailed backend conventions.

## Safe Defaults

- Search with `rg` when possible.
- Read large files in chunks.
- Avoid destructive commands unless explicitly requested.
- If you notice unrelated local changes, stop and confirm before modifying the same files.
