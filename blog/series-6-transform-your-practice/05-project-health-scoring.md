# Seeing the Cracks Before They Widen: Project Health Scoring

*Part 5 of "Run a Leaner Practice with Kazi."*

---

Every firm has an engagement that's quietly going sideways.

The budget was 40 hours. It's at 35 and only half the work items are done. Nobody flagged it because nobody checked — the partner is focused on the next engagement, the team is heads-down on the work, and the budget spreadsheet was last updated two weeks ago.

By the time someone notices, it's over budget. The client gets a larger-than-expected invoice, or the firm absorbs the overrun. Either way, trust erodes.

Kazi's project health scoring catches these problems early — before they become budget overruns, missed deadlines, or awkward client conversations.

## How Health Scores Work

Every engagement in Kazi has a health status: **Healthy**, **At Risk**, or **Critical**. The status is calculated automatically from live data — not from a subjective assessment by the partner.

The rules are simple and transparent:

**Critical (Red):**
- Budget is 100% consumed (over budget)
- More than 30% of work items are overdue

**At Risk (Amber):**
- Budget is 80%+ consumed but work item completion is below 80%
- More than 10% of work items are overdue
- No activity in the last 14 days (stale engagement)

**Healthy (Green):**
- None of the above conditions are true

**Unknown (Grey):**
- No work items created yet (can't assess without data)

## What This Looks Like in Practice

The dashboard shows a health widget — a list of engagements sorted by severity. Critical first, then At Risk, then Healthy.

```
Engagement Health — March 2026

🔴  Van der Merwe Trust — Annual AFS     Budget: 102%  |  Tasks: 4/12 overdue
🟡  Cape Quarter CC — Monthly Books      Budget: 84%   |  Tasks: 50% complete
🟡  Naidoo & Partners — Q1 VAT           No activity in 21 days
🟢  Thornton Properties — Tax Return     Budget: 45%   |  On track
🟢  (12 more engagements — all healthy)
```

At a glance: two engagements need attention. Van der Merwe is over budget with overdue tasks — the partner needs to intervene. Cape Quarter is burning budget faster than work is completing — scope might be expanding. Naidoo has gone quiet — is the engagement stalled?

The partner doesn't need to check each engagement individually. The health widget surfaces the problems.

## Budget Alerts: Catch Overruns at 80%, Not 100%

Every engagement can have a budget — in hours, in Rands, or both. When the team logs time, the budget consumption updates in real time.

At 80% consumption, the engagement turns amber. The partner gets a notification: "Cape Quarter CC — Monthly Bookkeeping has consumed 84% of its 20-hour budget. 10 hours of 12 work items are complete."

This is the intervention point. At 80%, you have options:
- **Talk to the client** about additional fees for expanded scope
- **Reassign work** to a lower-rate team member
- **Defer lower-priority items** to the next period
- **Adjust the budget** if the original estimate was too low

At 100%, your options shrink. The work is done, the hours are logged, and the conversation becomes "we went over budget" — reactive, not proactive.

The 80% alert turns budget management from a retrospective exercise into a real-time conversation.

## Stale Engagement Detection

Some engagements go quiet without anyone noticing. The client hasn't responded to a request. The team member assigned to it got pulled onto something urgent. The engagement sits in "Active" status with no activity for weeks.

Kazi flags engagements with no activity for 14+ days as "At Risk" with the reason: "No activity in 21 days."

This catches:
- **Engagements waiting on client input** — the info request was sent but nobody followed up
- **Deprioritized work** — the team moved on to more urgent engagements
- **Forgotten recurring work** — the quarterly VAT review that should've started last week

The stale flag isn't a problem by itself — some engagements naturally have gaps. But it's a prompt to check: "Is this engagement stalled, or is the gap intentional?"

## The Overdue Task Ratio

Individual overdue tasks are normal — deadlines shift, priorities change. But when 30%+ of an engagement's tasks are overdue, there's a systemic problem: the engagement is under-resourced, the timeline was unrealistic, or the scope expanded without adjusting the plan.

Kazi calculates the overdue ratio per engagement:

```
Thornton Properties — Tax Return
  Tasks: 8 total | 1 overdue (12.5%) → Healthy

Van der Merwe Trust — Annual AFS
  Tasks: 12 total | 4 overdue (33%) → Critical
```

The 33% overdue ratio on Van der Merwe signals that this engagement needs attention — not just "push the deadlines out" but "why are 4 tasks overdue? Is the team stuck? Is the client unresponsive? Is the scope wrong?"

## Health Scoring for Capacity Planning

Health scores aren't just about individual engagements — they're about firm-wide capacity.

If 3 of your 15 active engagements are At Risk or Critical, your team is likely over-committed. If all 15 are Healthy, you might have capacity for another client. If 5 are showing "No activity in 14 days," you have work that's stuck — freeing those bottlenecks would improve throughput without adding hours.

The dashboard's health widget is a capacity signal. Partners who check it daily make better decisions about when to take on new work, when to push back on timelines, and when to ask for help.

## What Changes

Firms using health scoring report two shifts:

**1. Earlier conversations.** The partner calls the client about scope at 80% budget, not 110%. The client appreciates the transparency ("thanks for letting us know before the bill arrived"). The relationship strengthens instead of straining.

**2. Fewer surprises at billing.** When the invoice goes out, the client has already been informed about additional hours. There's no "why is this R5,000 more than expected?" email — because the budget conversation happened at the 80% mark.

The health score doesn't do anything magical. It just makes the information visible that was always there — buried in time sheets, task lists, and unread email. Making it visible is what changes behaviour.

---

*[Request early access to Kazi →](#)*

*Next: [The Repeating Engagement Problem (And How to Automate It)](06-repeating-engagements.md)*
*Previous: [Your Clients Can Help Themselves](04-client-portal.md)*
