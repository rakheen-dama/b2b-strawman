# 664A: out-of-scope defects confirmed during OBS-after

## F-664A-1: K-100 remainder → 665F: Connections › Integrations cards overflow at 1024

- **Where:** `/org/e2e-test-org/settings/connections` (default tab Integrations, id `xero`), Alice, 1024×768 light. Stack: e2e :3001 built from worktree-epic-664A @ 14d5b9b84.
- **Measured** (`after/verify.json` → `k100.connections-xero-1024`):
  - The tab panel spans x 496–992 (496px wide; the rail is 192px under K-100 option A).
  - The cards grid `div.grid grid-cols-1 gap-6 md:grid-cols-2` has scrollWidth 556 in clientWidth 496.
  - The right-hand cards (AI Assistant, KYC Verification) extend to x = **1031** and **1052**, past the panel's right edge (992) and the viewport (1024). Their "Not Configured" badges and description lines are cut at the window edge. `document.scrollingElement.scrollWidth` = 1052.
  - 6 elements extend past the panel edge and 20 elements have inner overflow.
- **Expected:** the cards fit the content column at 1024, for example a single column below `xl` or a minimum card width that wraps.
- **Evidence:** `after/sheet-rail-k03-k100.png`, bottom-left tile ("connections?tab=xero — past-panel elements 6 … doc scrollWidth 1052").
- **Not affected:** Connections › AI at 1024 has 0 elements past the panel and document scrollWidth 1024.
- **Why out of scope:** K-100 option A (664A) only narrows the rail between md and xl. The card grid itself is 665F's territory. 664A improved this case (the panel was 456px before, now 496px), but it still overflows.
