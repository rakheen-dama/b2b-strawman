# Fix Spec: GAP-D0-07 — E2E seed does not pre-apply legal-za profile

## Problem
The E2E seed script provisions the tenant with the `accounting-za` vertical profile. The QA test plan for the legal lifecycle expects the `legal-za` profile to be pre-applied. The QA agent must manually switch profiles via Settings > General, which is a workaround but causes downstream issues (e.g., GAP-D0-01 — project templates not seeded because the profile switch doesn't trigger all pack seeders).

## Root Cause (confirmed)
In `compose/seed/seed.sh` line 39:
```json
"verticalProfile": "accounting-za"
```

This is hardcoded to the accounting profile. For legal QA, it should be `legal-za`.

## Fix
The E2E seed script should support a configurable vertical profile via environment variable.

### 1. In `compose/seed/seed.sh`, change line 39:

**Before:**
```bash
    \"verticalProfile\": \"accounting-za\"
```

**After:**
```bash
    \"verticalProfile\": \"${VERTICAL_PROFILE:-accounting-za}\"
```

### 2. In `compose/docker-compose.e2e.yml`, add environment variable to the seed service:

Under the `seed` service, add:
```yaml
environment:
  VERTICAL_PROFILE: ${VERTICAL_PROFILE:-accounting-za}
```

### 3. For the QA cycle, set the environment variable before starting:

```bash
VERTICAL_PROFILE=legal-za bash compose/scripts/e2e-up.sh
```

### Alternative (simpler for QA only):
Modify the seed script to accept a command-line argument or just change the hardcoded value to `legal-za` on the bugfix branch. This is acceptable since the branch is QA-specific.

## Scope
Seed / Docker
Files to modify:
- `compose/seed/seed.sh` (line 39)
- `compose/docker-compose.e2e.yml` (seed service environment)
Files to create: none
Migration needed: no

## Verification
1. Run `VERTICAL_PROFILE=legal-za bash compose/scripts/e2e-up.sh`
2. Navigate to Settings > General — profile should show "Legal (South Africa)"
3. Sidebar should show legal nav items (Court Calendar, Conflict Check, etc.)
4. Settings > Project Templates should show 4 legal matter templates (when combined with GAP-D0-01 fix)

## Estimated Effort
S (< 30 min)
