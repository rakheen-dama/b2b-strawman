# 665A: out-of-scope defects found during OBS-before (reported, not fixed; root CLAUDE.md §7)

665A restyles these tabs frame-only. Both defects below change stored data, so they need their own re-spec.

## F-665A-1: General shows a null brand colour as `#000000`, and "Save Settings" would persist it

- **Where:**
  - `frontend/app/(app)/org/[slug]/settings/firm/_tabs/general.tsx:54`: `brandColor={settings.brandColor ?? "#000000"}`
  - `frontend/components/settings/general-settings-form.tsx:149`: `brandColor: brandColor || undefined`
  - `OrgSettingsService.updateSettingsWithBranding` (`backend/.../settings/OrgSettingsService.java:179`) sets the brand colour to whatever it is sent, including null.
- **Observed:**
  - `GET /api/settings` → `brandColor: null` (`before/discover-pre.json`).
  - The form shows `#000000` in both the "Brand Color" text input and the swatch (`before/measurements.json` → `personas.alice.tabs.general.controls`).
- **Consequence (from the code; not executed, because it would change the stack):**
  - An owner who changes only the currency or the tax label and presses "Save Settings" also stores `#000000` as the firm's brand colour.
  - `PortalBrandingService.java:83` returns the colour unchanged to the portal, and the report templates apply it whenever it is not empty.
  - In this capture the save was made a true no-op by clearing the field first ("" → `undefined` → null).
- **Proposed (needs a decision):** show an empty field for null (the input already accepts "", because `colorValid = !brandColor || …`). Alternatively, send the brand colour only when it was changed.

## F-665A-2: Compliance shows a dormancy threshold of 365 days while the backend uses 90, and "Save Settings" would persist 365

- **Where:**
  - `frontend/app/(app)/org/[slug]/settings/firm/_tabs/compliance.tsx:80`: `dormancyThresholdDays={settings.dormancyThresholdDays ?? 365}`
  - `backend/.../compliance/CustomerLifecycleService.java:220-223`: when it is null, `runDormancyCheck()` uses **90**.
- **Observed:**
  - `GET /api/settings` → `dormancyThresholdDays: null` (`before/discover-pre.json`).
  - The "Dormancy Threshold (days)" input shows **365** (`before/measurements.json` → `personas.alice.tabs.compliance.controls`).
- **Consequence (from the code; not executed):**
  - The page states a threshold that is not in effect: 365 shown, 90 applied.
  - The card's single "Save Settings" saves dormancy, deadline and Phase 93's three FICA review intervals together. Changing only a FICA interval therefore also changes the effective dormancy threshold from 90 to 365 days, with no visible change on the page.
  - The deadline has the same `?? 30` fallback, but there it matches the backend: null resolves to the jurisdiction default, 30 for ZA (`DataSubjectRequestService.java:255-261`, `JurisdictionDefaults.java:20`). So only dormancy is wrong.
- **Proposed (needs a decision):** align the fallback with the backend (90), or have the backend return its effective value. If neither, show null as empty with a "default" hint. The `compliance.tsx:74-76` comment already states the intent ("fallbacks would overwrite the firm's real dormancy … unseen"); only the load-failure path is guarded today.
