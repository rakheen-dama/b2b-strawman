# 650D found — OBS gate

No F-OBS findings: the slice's own behaviour (lockedThrough, the locked-month warning in the five
dialogs and on the TrustBalanceCard mount site) matched the acceptance criteria on every check.

Observations (pre-existing, outside the slice; reported, not fixed here):

- **O-OBS-1 — Record Transfer and Record Fee Transfer ask for raw UUIDs.** The Transactions-page
  dialogs label their inputs "Source Client ID" / "Target Client ID" / "Source Matter" / "Target
  Matter" (placeholders "Source client UUID", …) and "Client ID" / "Fee note ID" ("Client UUID",
  "Fee note UUID"). Not a 650D change (the slice only adds the warning). Reproduce: Trust ›
  Transactions › Record transaction › Record Transfer (`gates/obs/w-transfer-warning-1280.png`).
- **O-OBS-2 — TrustBalanceCard "Last transaction" date format.** The card renders
  "Last transaction: 2026/10/28" (`toLocaleDateString("en-ZA")`, `TrustBalanceCard.tsx`), while the
  rest of the trust UI writes "28 Oct 2026". Pre-existing. Reproduce: Clients › Acme Corp › Fees ·
  Trust (`gates/obs/c-01-client-trust-tab.png`).
