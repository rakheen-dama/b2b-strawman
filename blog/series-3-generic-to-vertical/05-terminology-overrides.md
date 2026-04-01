# Terminology Overrides: Projects → Engagements Without Forking the Frontend

*Part 5 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries.*

---

An accounting firm doesn't have "projects." They have "engagements." They don't have "customers." They have "clients." They don't send "proposals." They send "engagement letters."

A law firm doesn't have "projects" either. They have "matters." "Time entries" are "fee notes." "Documents" might be "pleadings." A "rate card" is a "tariff schedule."

If your software shows "Projects" to an accounting firm, they'll use it — but it will always feel like a generic tool that doesn't understand their practice. The words matter. They signal whether the software was built *for them* or merely *sold to them*.

DocTeams handles this through a terminology override system that translates generic terms to industry-specific language based on the tenant's vertical profile. No code changes. No frontend forks. Just a lookup table.

## The Terminology Map

Each vertical defines a mapping from generic terms to industry terms:

```typescript
export const TERMINOLOGY: Record<string, Record<string, string>> = {
  "accounting-za": {
    Project: "Engagement",
    Projects: "Engagements",
    project: "engagement",
    projects: "engagements",
    Customer: "Client",
    Customers: "Clients",
    customer: "client",
    customers: "clients",
    Proposal: "Engagement Letter",
    Proposals: "Engagement Letters",
    "Rate Card": "Fee Schedule",
    "Rate Cards": "Fee Schedules",
  },
  "legal-za": {
    Project: "Matter",
    Projects: "Matters",
    project: "matter",
    projects: "matters",
    Customer: "Client",
    Customers: "Clients",
    Task: "Work Item",
    Tasks: "Work Items",
    "Time Entry": "Fee Note",
    "Time Entries": "Fee Notes",
    "Rate Card": "Tariff Schedule",
    Document: "Pleading",
    Documents: "Pleadings",
  },
};
```

The map covers both singular and plural forms, capitalized and lowercase. This handles the majority of UI text: page titles ("Engagements"), breadcrumbs ("engagement"), table headers ("Fee Notes"), empty states ("No engagements yet").

## The Translation Hook

A React context provides the `t()` function to all components:

```typescript
export function TerminologyProvider({ verticalProfile, children }) {
  const value = useMemo(() => {
    const map = verticalProfile
      ? (TERMINOLOGY[verticalProfile] ?? {})
      : {};
    return {
      verticalProfile,
      t: (term: string) => map[term] ?? term,  // Fallback to original
    };
  }, [verticalProfile]);

  return (
    <TerminologyContext.Provider value={value}>
      {children}
    </TerminologyContext.Provider>
  );
}
```

The `t()` function does a simple lookup: if the term exists in the current vertical's map, return the replacement. Otherwise, return the original. This means:

- `t("Projects")` → "Engagements" (for accounting-za)
- `t("Projects")` → "Matters" (for legal-za)
- `t("Projects")` → "Projects" (for no vertical / generic)

Components use it naturally:

```tsx
function ProjectsPage() {
  const { t } = useTerminology();

  return (
    <div>
      <h1>{t("Projects")}</h1>
      <p>Manage your {t("projects")} and track progress.</p>
      <Button>{t("Create")} {t("Project")}</Button>
    </div>
  );
}
```

For strings with embedded terms, a `TerminologyText` component handles template interpolation:

```tsx
// "No {projects} yet" → "No engagements yet" (accounting)
// "No {projects} yet" → "No matters yet" (legal)
<TerminologyText template="No {projects} yet" />
```

## What This Approach Can and Can't Do

**Can do:**
- Translate noun-based terms (Projects → Engagements)
- Handle singular/plural separately
- Handle case variations (Project, project, PROJECTS)
- Fall back gracefully for unmapped terms
- Work with zero performance overhead (memoized lookup)

**Can't do:**
- Grammar-sensitive translations. "Create a new project" → "Create a new engagement" works. But "The project's timeline" → "The engagement's timeline" also works because the possessive structure is the same. You'd hit problems with languages where grammar changes with the noun.
- Full sentence translation. This isn't i18n (internationalization) — it's vocabulary substitution within English. If you need full multi-language support, use a proper i18n library and put terminology overrides into the translation files.
- Dynamic discovery. The map is static — defined in code, loaded at build time. Adding a new term override requires a code change (to the terminology map file), not a database update. For my use case, this is fine — terminology changes are rare and always accompany a vertical profile update.

## The Design Tradeoff

I considered three approaches:

**Option A: Full i18n** (react-intl, next-intl). Use locale files per vertical (`en-ZA-accounting.json`, `en-ZA-legal.json`). Every string in the UI goes through the i18n system.

Pro: Maximum flexibility. Every string is translatable.
Con: Massive upfront effort. Every component needs i18n keys. 89 pages × ~20 strings each = ~1,800 keys to define.

**Option B: Terminology map** (what I built). A simple lookup for ~30 domain terms per vertical. Non-mapped strings pass through unchanged.

Pro: 30 lines per vertical. Works today. Easy to add new verticals.
Con: Limited to vocabulary substitution. Can't translate "Your project is overdue" to a fundamentally different sentence.

**Option C: Database-driven terminology**. Store overrides in the tenant's OrgSettings and load them per request.

Pro: Tenants could customize their own terminology.
Con: Adds a database query to every page render. Overly complex for a feature that changes once at setup time.

I went with Option B because it covers 90% of the need with 10% of the effort. The remaining 10% (full sentence translations, tenant-customizable terms) isn't needed for the target market. Small South African firms operate in English. They just want their own jargon.

If the product expands to non-English markets, I'd upgrade to Option A — but at that point, I'd need full i18n anyway for language support, and the terminology overrides would fold into the translation files naturally.

## Where Overrides Show Up

The terminology system touches:

- **Page titles and headings**: "Engagements" instead of "Projects"
- **Sidebar navigation**: "Engagements", "Clients", "Fee Schedules"
- **Breadcrumbs**: Dashboard > Client Name > Engagement Name
- **Empty states**: "No engagements yet. Create your first engagement."
- **Button labels**: "Create Engagement", "Log Fee Note"
- **Table headers**: "Engagement Name", "Client", "Status"
- **Notifications**: "New comment on engagement 'Monthly Bookkeeping'"

The gap report (Post 4) found that some page titles and breadcrumbs weren't using `t()` — they had hardcoded strings. This is the typical adoption pattern: you build the system, then systematically audit every string in the UI to ensure it goes through `t()`. The audit found about 15 places where the term was hardcoded.

## A Small Feature With Big Impact

Terminology overrides are maybe 200 lines of code total. The React context, the map, the `TerminologyText` component, and the hook. Trivial to build, trivial to maintain.

But the impact on how the product *feels* to a vertical customer is outsized. When an accounting firm opens DocTeams and sees "Engagements" instead of "Projects," they unconsciously evaluate the product as purpose-built. The compliance packs and custom fields do the heavy lifting — but the terminology is the first thing they see.

It's the cheapest vertical customization you can make, and it should be the first one you build.

---

*Next in this series: [South African Professional Services: A Surprisingly Good First Market](06-south-african-market.md)*

*Previous: [The Gap Report](04-the-gap-report.md)*
