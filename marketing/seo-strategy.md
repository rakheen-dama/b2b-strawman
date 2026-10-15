# Kazi — SEO Strategy (Channel #4, in service of pilots)

> Per the GTM plan, content/SEO is the **fourth-ranked** channel — behind direct contacts, domain-expert introducers, and trust auditors. It exists to make Kazi findable and credible when a warm lead Googles us, and to compound slowly toward inbound. Do not over-invest: this is a **keyword strategy now, content later** play. Budget: a few hours/week at most until a pilot is signed.

## Architecture decision (2026-07-13 ideation)

**Dual-track site:** `kazi.africa` homepage stays horizontal; a new **`kazi.africa/legal`** landing page carries the vertical message (matters, trust accounting, FICA, fee notes) and is the destination for the elevator pitch, LinkedIn posts, and every legal-content piece. Spec: `requirements/claude-code-prompt-phase85.md`.

All SEO content targets `/legal` (and later `/blog/*` on the same domain — never a subdomain; keep the equity on kazi.africa).

## Who searches, and what they type

The buyer (managing partner) rarely Googles "practice management software" cold — they Google **their problems**, usually near a compliance deadline or audit. The bookkeeper/practice manager Googles operational how-tos. Auditors and candidate attorneys Google statutes. That ordering gives us the clusters:

### Cluster 1 — Trust accounting & s86 (highest intent-to-pain match, low competition)
- "section 86 Legal Practice Act trust account"
- "attorney trust account rules South Africa"
- "trust account audit requirements attorneys"
- "three-way trust reconciliation" / "trust reconciliation attorneys"
- "LPC trust account rules"

### Cluster 2 — FICA for law firms
- "FICA requirements for law firms" / "FICA checklist attorneys"
- "law firm accountable institution FICA"
- "FICA client onboarding documents attorney"

### Cluster 3 — Billing & fee notes
- "attorney fee note template" / "fee note vs invoice attorney"
- "contingency fee cap South Africa"
- "law firm billing software South Africa"
- "unpaid legal fees collect attorney"

### Cluster 4 — Category & switching (commercial intent, do these later)
- "legal practice management software South Africa"
- "GhostPractice alternative" / "AJS legal software alternative" / "Lexpro alternative"
- Comparison pages only once we can be factually scrupulous — a small vendor caught exaggerating loses the trust game that continuity messaging is winning.

### Cluster 5 — AI for law firms (low volume, high novelty — LinkedIn is the better channel)
- "AI for law firms South Africa", "Claude for lawyers"
- Write one definitive page when the MCP story is publicly demoable; otherwise let LinkedIn carry it.

## Content plan (maps to existing drafts)

| Piece | Cluster | Source material | Priority |
|---|---|---|---|
| LPA s86 explained (plain language) | 1 | New — GTM plan names it | **1** |
| What your trust audit actually checks | 1 | New — auditor channel dual-use | **2** |
| FICA for small firms, without the filing cabinet | 2 | `blog/series-6/03-fica-without-filing-cabinet.md` (re-angle to legal) | **3** |
| Stop billing late (the fee-note run) | 3 | `blog/series-6/02-stop-billing-late.md` (re-angle) | 4 |
| Which clients are profitable | 3 | `blog/series-6/01-which-clients-are-profitable.md` | 5 |
| A client portal your clients will use | 3/4 | `blog/series-6/04-client-portal.md` | 6 |

Rules: each piece is genuinely useful with Kazi absent from the first 80% (statute explainers earn links and auditor respect); one CTA at the end → `/legal`. 1,200–2,000 words. Author = founder by name (E-E-A-T + it's true).

**Publishing note:** the site has no blog engine (deliberately out of scope in Phase 85). Until one exists, publish pieces as LinkedIn articles + PDF leave-behinds; the blog build is a later, pilot-funded decision. Cluster-1 pieces are the exception worth static pages early — consider shipping "s86 explained" as a second static page alongside `/legal` if it can ride the same phase cheaply.

## Technical SEO (lands with Phase 85's `/legal` page)

- Unique title/description via Next.js Metadata API; OG/Twitter cards with a product screenshot.
- JSON-LD: `SoftwareApplication` (+ `Offer` once pricing is public) and `FAQPage` for the objection FAQ section.
- `sitemap.xml` + `robots.txt` (verify they exist for the current site; add if not).
- Static/RSC render — no client JS needed for content; Core Web Vitals should be free wins.
- Register Google Search Console + Bing Webmaster; that's the entire measurement stack for now.

## What we deliberately do not do

- No paid search until a pilot converts and CAC has a denominator.
- No programmatic/AI-generated content farms — one credibility hit outweighs any traffic.
- No standalone-blog monetization (parked per GTM plan; series 1–5 developer content serves the tech-deck/hiring narrative, not this funnel).
- No competitor-bashing comparison pages in v1.

## Review trigger

Revisit after the first pilot signs, or after 90 days of Search Console data — whichever comes first. The question then: did any inbound lead actually arrive via search? If no, freeze content spend and double down on channels 1–3.
