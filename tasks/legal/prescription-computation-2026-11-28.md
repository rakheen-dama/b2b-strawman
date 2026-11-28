# When does prescription complete? Legal opinion for founder sign-off (2026-11-28)

**Status:** needs founder decision. This is an advisory opinion from an AI legal-advisor agent with cited sources. It is **not** legal advice, and an admitted SA attorney must confirm it before go-live.
**Raised by:** PR #1889 (prescription tracker days-left fix), reviewer A.
**Research:** Perplexity search over judgment extracts. Full-text SAFLII fetches were blocked (403). Truter v Deysel and Uniform Rule 4(1)(c) are cited from memory.

## Key finding: Kazi's stored date is one day later than the date courts use
`PrescriptionRuleRegistry.calculatePrescriptionDate` stores `causeOfActionDate.plusYears(n)`, which is the **anniversary**. Under SA law, prescription completes at **midnight at the end of the day before the anniversary**. So Kazi's "prescription date" is the **first day the claim has already prescribed**.

The risk: a practitioner who reads "Prescription date: 29 Sept" as the last day to serve summons would serve **one day late**.

## Answers

| Question | Answer | Confidence |
|---|---|---|
| When does prescription complete? | Midnight at the end of the day before the anniversary. The civilian method applies: count the first day, exclude the last. Interpretation Act s4 does **not** apply. | High |
| Which date should the tracker store and display? | The **last day to serve**, i.e. the anniversary minus one day. That matches court usage: "prescribed on 7 Feb 2017" in Molokwane, "not later than midnight on the 28th" in Afrisun. | Medium-high |
| Expired on the stored date, or the day after? | With today's stored value (the anniversary), "Expired from that date" is **correct**. If the store changes to the last day to serve, show Expired only from the next day. | High |
| Interrupt hidden once Expired? | This is a flaw. Interrupt records a past event (service of summons), so check the **service date** against the last day, not today's date. | High |
| Weekends and public holidays | Count calendar years; never extend the countdown. If the last day is a Sunday or a public holiday, warn the user and point to the preceding court day. RAF v Masindi extends RAF periods to the next court day, but that has not been applied by the SCA to s11. | Medium (unsettled) |

Authorities:
- **Kleynhans v Yorkshire Insurance** 1957 (3) SA 544 (A): service on the anniversary was one day late. Cited via SALRC DP 126, paras 3.60–3.67.
- **Afrisun KZN v Sewpersad** [2017] ZAKZPHC 50: applies the same rule to s11(d); s4 is rejected.
- **Minister of Police v Molokwane** [2022] ZASCA 111, para 23: consistent with the above. This case is not primarily about how to count.
- **Truter v Deysel** 2006 (4) SA 168 (SCA): when the debt becomes due (the start of the period).
- **Arendsnes Sweefspoor v Botha** [2013] ZASCA 86: only **service** interrupts prescription (s15(1)).
- **Somdaka v Northern Assurance** 1961 (4) SA 764 (N): a period whose last day is a Sunday still ends on that Sunday.
- **RAF v Masindi** [2018] ZASCA 94: RAF periods extend to the next court day. Followed in **Gabuza** and **Mabaza** [2025] ZAGPJHC 413.

## Options for the founder
- **(A) Recommended by the advisor:**
  - store and display the **last day to serve** (anniversary − 1);
  - show Expired only from the day after;
  - keep Interrupt available through that date, checking the service date entered;
  - never extend for weekends or holidays.

  This needs a backend change plus a data migration for existing trackers, or recomputation from the cause-of-action date.
- **(B) Smaller change:** keep the stored anniversary and the current expiry rule, but relabel the date "Prescribed from" and show "Last day to serve" as the day before.
- **Either way:**
  - fix Interrupt so it checks the service date;
  - add a disclaimer that the date is a calculated aid and depends on the s12 knowledge rule and s13 delays.

## For an admitted SA attorney to confirm before go-live
1. The first-in-last-out rule for s11, and the minus-one-day rule, including cause-of-action dates on 29 February.
2. Whether "last day to serve" matches how firms diarise these dates.
3. Whether to show a Masindi note at all for claims outside the RAF.
4. The rule against Sunday service, under both the High Court and Magistrates' Court rules.
5. Which special regimes need their own tracker types: RAF Act s23; the six-month notice under Act 40 of 2002; the 15-year period for State debts.
6. That the "cause of action date" field really is the s12 "debt due" date.
