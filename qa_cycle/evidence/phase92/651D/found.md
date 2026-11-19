# 651D — found (outside the slice)

| id | where | finding | owner |
|---|---|---|---|
| F-651D-1 | `kazi-architecture/20-cross-cutting/multi-vertical.md` §5, `30-modules/vertical-profiles.md` §10.1, `60-verticals/legal-za.md` §9.1, `30-modules/trust-accounting.md` §10.1, `50-flows/payment-receipt-to-trust-allocation.md` (last failure mode), `50-flows/pack-install-and-vertical-onboarding.md:112`; check `packs.md:153`, `tenancy-provisioning.md:78` | Docs say that after a vertical-profile switch `trust_accounting` stays enabled and the trust API stays reachable. The code drops VERTICAL modules on a switch (`OrgSettingsService.updateVerticalProfile:1035` keeps horizontal modules only; `trust_accounting` is `VERTICAL` at `VerticalModuleRegistry.java:52`), so the guards then refuse. Pre-Phase-92 staleness; found by CodeRabbit on PR #1813. | separate docs PR (decision 651D D-7) |
| F-651D-2 | `kazi-architecture/30-modules/customer-portal.md:172` | Says the portal trust nav gate is "profile + module"; `portal/lib/nav-items.ts:37` filters on `modules` only (no `profiles` since #1639). | same docs PR as F-651D-1 |
