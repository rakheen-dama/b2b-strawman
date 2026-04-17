# Fix Spec: GAP-L-02 — Untranslated i18n key `expiredActionMessage` on KC info page

## Problem

From `day-00.md` §0.22: after the invite token is consumed, the KC expired-link page displays the
raw i18n key `expiredActionMessage` as its heading (instead of the translated string "Action
expired. Please continue with login now."). The body text IS properly localized; only the heading
shows the key.

Screenshot: `qa_cycle/checkpoint-results/day-00-invite-token-expired.png`.

## Root Cause (validated)

Validated via grep/read:

1. **Our custom `Info.tsx` renders `messageHeader` as a raw string**:
   `compose/keycloak/theme/src/login/pages/Info.tsx` line 15:
   ```ts
   const title = messageHeader ?? "Information";
   ```
   And line 21: `<Layout title={title}>` — the `title` is rendered verbatim.

2. **The keycloakify reference implementation runs `messageHeader` through `advancedMsgStr`**:
   `compose/keycloak/theme/node_modules/keycloakify/src/login/pages/Info.tsx` line 23:
   ```ts
   __html: kcSanitize(messageHeader ? advancedMsgStr(messageHeader) : message.summary)
   ```
   `messageHeader` is an i18n key emitted by Keycloak core (e.g., `expiredActionMessage`) that
   MUST be translated. Our custom override dropped this translation step.

3. **The key is defined in keycloakify's default bundle**:
   `compose/keycloak/theme/node_modules/keycloakify/src/login/i18n/messages_defaultSet/en.ts`
   line 225: `expiredActionMessage: "Action expired. Please continue with login now."`

Conclusion: the key IS available — we just forgot to translate it. **No missing bundle entry, no
custom message needed** — purely a translation call that our custom template skipped.

## Fix

Edit `compose/keycloak/theme/src/login/pages/Info.tsx`:

1. Import `advancedMsgStr` from `i18n`:
   Line 14 already destructures `{ advancedMsgStr }` — good, no import change needed.

2. Replace line 15:
   ```ts
   const title = messageHeader ?? "Information";
   ```
   with:
   ```ts
   const title = messageHeader ? advancedMsgStr(messageHeader) : "Information";
   ```

3. Optional polish (same commit): same fix pattern should be applied to
   `compose/keycloak/theme/src/login/pages/Register.tsx` — see GAP-L-03's spec. The two changes
   can ship together.

Then rebuild the theme JAR:

```bash
cd compose/keycloak/theme
pnpm install && pnpm run build-keycloak-theme
cp dist_keycloak/keycloak-theme.jar ../providers/keycloak-theme.jar
docker restart b2b-keycloak
```

## Scope

- **Keycloak theme**: YES.
- **Backend / Frontend / Gateway / Seed**: NO.
- **Realm JSON changes**: NO.

Files to modify:
- `compose/keycloak/theme/src/login/pages/Info.tsx` (one line change).

Files to create: none.
Migration needed: NO.
JAR rebuild required: YES.
KC restart required: YES.

## Verification

After fix: trigger the expired-link page (click an already-consumed invite URL). Expected heading:
"Action expired. Please continue with login now." — NOT the raw key.

Paired with GAP-L-01's full fix: once GAP-L-01 is resolved the expired-link page should rarely be
reached, but this polish ensures any legitimate expired-link cases (e.g., 24h+ old emails) render
cleanly.

## Estimated Effort

**S (~10 min)**: one-line edit + theme rebuild. JAR rebuild is ~1 min.

## Notes

This is genuinely a bug in our custom Keycloakify override — not a KC core issue. Rejecting
WONT_FIX since the fix is trivial and we already ship a custom theme.

If batched with GAP-L-03 (same file area, same build pipeline, same KC restart), both can ship in
a single JAR rebuild.
