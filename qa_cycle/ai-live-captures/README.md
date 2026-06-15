# AI Live-Claude Response Captures

Real Claude responses captured during the AI-core live verification cycle (2026-06-14), pulled
from `ai_executions.output_content` in the verifain test tenant. Purpose:

1. **Mock/stub fixtures** — feed these to `StubAiProvider` (or a future "replay" provider) so tests
   and demos exercise the full skill pipeline against realistic output **without live API spend**.
   They are raw Claude output (markdown ```json-fenced), which is exactly what the parsing layer
   (`LlmJsonParser`) must tolerate — so they double as regression fixtures for AIVERIFY-001.
2. **Contract reference** — the real field shapes each skill returns, for keeping the backend DTOs
   and frontend types honest.

## Convention

- One subdir / prefix per skill id: `matter-intake-*`, `fica-verification-*`, `contract-review-*`,
  `drafting-*`, `compliance-audit-*`.
- Files are the **raw** `output_content` (fence included) — do not pre-strip; that's the point.
- To promote one to a test stub: strip the fence and drop the JSON at
  `backend/src/test/resources/ai/stubs/{skill-id}/response.json`.

## Captured so far

| File | Skill | Notes |
|------|-------|-------|
| `matter-intake-live-1..4.txt` | matter-intake | LITIGATION classification, shareholders'-agreement dispute; conflict screen CLEAR; prescription risk flag. `-4` is the genuine first call (1153 input tokens). |
