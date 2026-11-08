# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: projects/project-time.spec.ts >> PROJ-03: Time Entries >> 7. My Work lists today's time entries
- Location: e2e/tests/projects/project-time.spec.ts:238:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Time Test Project u06g — Non-billable entry u06g')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for getByText('Time Test Project u06g — Non-billable entry u06g')

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e6]: Kazi
      - generic [ref=e10]: E2E Test Organization
      - button "Search, Command K" [ref=e12]:
        - img [ref=e13]
        - text: Search...
        - generic [ref=e16]: ⌘K
      - navigation "Main navigation" [ref=e17]:
        - generic [ref=e18]:
          - button "Today" [expanded] [ref=e19]:
            - generic [ref=e20]: Today
            - img [ref=e21]
          - generic [ref=e23]:
            - link "Home" [ref=e24] [cursor=pointer]:
              - /url: /org/e2e-test-org/dashboard
              - img [ref=e25]
              - text: Home
            - link "My work" [ref=e28] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work
              - img [ref=e30]
              - text: My work
            - link "Diary" [ref=e33] [cursor=pointer]:
              - /url: /org/e2e-test-org/diary
              - img [ref=e34]
              - text: Diary
        - generic [ref=e37]:
          - button "Matters" [expanded] [ref=e38]:
            - generic [ref=e39]: Matters
            - img [ref=e40]
          - generic [ref=e42]:
            - link "Matters" [ref=e43] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e44]
              - text: Matters
            - link "New matter" [ref=e48] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e49]
              - text: New matter
            - link "Conflict check" [ref=e50] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e51]
              - text: Conflict check
            - link "Adverse parties" [ref=e55] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e56]
              - text: Adverse parties
        - button "Fees" [ref=e64]:
          - generic [ref=e65]: Fees
          - img [ref=e66]
        - button "Trust" [ref=e70]:
          - generic [ref=e71]: Trust
          - img [ref=e72]
        - button "Firm" [ref=e76]:
          - generic [ref=e77]: Firm
          - img [ref=e78]
      - link "Notifications" [ref=e82] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e83]
        - text: Notifications
      - generic [ref=e87]:
        - generic [ref=e88]: CM
        - generic [ref=e89]:
          - paragraph [ref=e90]: Carol Member
          - paragraph [ref=e91]: carol@e2e-test.local
    - generic [ref=e92]:
      - banner [ref=e93]:
        - navigation "Breadcrumb" [ref=e94]:
          - link "E2E Test Organization" [ref=e95] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e96]:
            - img [ref=e97]
            - generic "My work" [ref=e99]
        - generic [ref=e100]:
          - generic [ref=e102]: e2e-test-org
          - button "CM" [ref=e104]
          - button "Notifications" [ref=e106]:
            - img
            - generic [ref=e107]: 3 unread notifications
            - generic [ref=e108]: "3"
      - main [ref=e109]:
        - generic [ref=e112]:
          - generic [ref=e113]:
            - generic [ref=e114]:
              - generic [ref=e115]:
                - heading "My Work" [level=1] [ref=e116]
                - paragraph [ref=e117]: Your tasks and time tracking across all matters
              - generic [ref=e118]:
                - button "This Week" [ref=e119]
                - button "This Month" [ref=e120]
                - button "Last 30 Days" [ref=e121]
                - button "This Quarter" [ref=e122]
                - button "Custom" [disabled]
                - button "Refresh data" [ref=e123]:
                  - img
            - link "Timesheet" [ref=e124] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work/timesheet
          - generic [ref=e125]:
            - generic [ref=e126]:
              - generic [ref=e127]:
                - generic [ref=e128]:
                  - paragraph [ref=e129]: Today's diary
                  - link "Open diary" [ref=e130] [cursor=pointer]:
                    - /url: /org/e2e-test-org/diary?view=day
                - paragraph [ref=e131]: Nothing in the diary today
              - generic [ref=e132]:
                - paragraph [ref=e133]: Time Today
                - generic [ref=e135]:
                  - generic [ref=e136]: 2h 15m
                  - generic [ref=e137]: / 8h
              - generic [ref=e140]:
                - paragraph [ref=e141]: Next Deadline
                - paragraph [ref=e142]: No upcoming deadlines
            - generic [ref=e144]:
              - generic [ref=e145]:
                - paragraph [ref=e146]: Upcoming · next 14 days
                - link "Open diary list" [ref=e147] [cursor=pointer]:
                  - /url: /org/e2e-test-org/diary?view=list&from=2026-11-09&feeEarner=mine
              - paragraph [ref=e148]: Nothing in the diary for the next 14 days
          - generic [ref=e149]:
            - generic [ref=e150]:
              - 'button "Mon: 0.0 hours logged" [ref=e151] [cursor=pointer]':
                - generic [ref=e153]: Mon
              - 'button "Tue: 0.0 hours logged" [ref=e154] [cursor=pointer]':
                - generic [ref=e156]: Tue
              - 'button "Wed: 0.0 hours logged" [ref=e157] [cursor=pointer]':
                - generic [ref=e159]: Wed
              - 'button "Thu: 0.0 hours logged" [ref=e160] [cursor=pointer]':
                - generic [ref=e162]: Thu
              - 'button "Fri: 0.0 hours logged" [ref=e163] [cursor=pointer]':
                - generic [ref=e165]: Fri
              - 'button "Sat: 3.5 hours logged" [ref=e166] [cursor=pointer]':
                - generic [ref=e169]: Sat
              - 'button "Sun: 2.3 hours logged" [ref=e170] [cursor=pointer]':
                - generic [ref=e173]: Sun
            - generic [ref=e174]:
              - paragraph [ref=e175]: 5.8h
              - paragraph [ref=e176]: this week
          - generic [ref=e177]:
            - generic [ref=e179]:
              - generic [ref=e181]:
                - generic [ref=e182]:
                  - img [ref=e183]
                  - 'button "Help: Saved views" [ref=e186] [cursor=pointer]':
                    - img [ref=e187]
                - tablist [ref=e191]:
                  - tab "All" [selected] [ref=e192]
              - generic [ref=e193]:
                - generic [ref=e194]:
                  - heading "My Tasks" [level=2] [ref=e195]
                  - generic [ref=e196]: "2"
                - generic [ref=e198]:
                  - generic [ref=e199]:
                    - img [ref=e201]
                    - heading "No Due Date" [level=3] [ref=e203]
                    - generic [ref=e204]: "2"
                  - table [ref=e207]:
                    - rowgroup [ref=e208]:
                      - row "Matter Title Priority Status Due Date Logged" [ref=e209]:
                        - columnheader "Matter" [ref=e210]
                        - columnheader "Title" [ref=e211]
                        - columnheader "Priority" [ref=e212]
                        - columnheader "Status" [ref=e213]
                        - columnheader "Due Date" [ref=e214]
                        - columnheader "Logged" [ref=e215]
                    - rowgroup [ref=e216]:
                      - row "Bell Matter tv86 Bell Task Listed tv86 Medium Open — —" [ref=e217] [cursor=pointer]:
                        - cell "Bell Matter tv86" [ref=e218]:
                          - generic [ref=e219]: Bell Matter tv86
                        - cell "Bell Task Listed tv86" [ref=e220]:
                          - paragraph [ref=e222]: Bell Task Listed tv86
                        - cell "Medium" [ref=e223]:
                          - generic [ref=e224]: Medium
                        - cell "Open" [ref=e225]:
                          - generic [ref=e226]: Open
                        - cell "—" [ref=e227]:
                          - generic [ref=e228]: —
                        - cell "—" [ref=e229]
                      - row "Bell Matter tv86 Bell Task Opened tv86 Medium Open — —" [ref=e230] [cursor=pointer]:
                        - cell "Bell Matter tv86" [ref=e231]:
                          - generic [ref=e232]: Bell Matter tv86
                        - cell "Bell Task Opened tv86" [ref=e233]:
                          - paragraph [ref=e235]: Bell Task Opened tv86
                        - cell "Medium" [ref=e236]:
                          - generic [ref=e237]: Medium
                        - cell "Open" [ref=e238]:
                          - generic [ref=e239]: Open
                        - cell "—" [ref=e240]:
                          - generic [ref=e241]: —
                        - cell "—" [ref=e242]
              - generic [ref=e243]:
                - button "Available Tasks 21" [ref=e244]:
                  - img [ref=e245]
                  - heading "Available Tasks" [level=2] [ref=e247]
                  - generic [ref=e248]: "21"
                - table [ref=e251]:
                  - rowgroup [ref=e252]:
                    - row "Matter Title Priority Due Date" [ref=e253]:
                      - columnheader "Matter" [ref=e254]
                      - columnheader "Title" [ref=e255]
                      - columnheader "Priority" [ref=e256]
                      - columnheader "Due Date" [ref=e257]
                  - rowgroup [ref=e258]:
                    - row "Time Test Project u06g Time Task u06g Medium —" [ref=e259] [cursor=pointer]:
                      - cell "Time Test Project u06g" [ref=e260]:
                        - generic [ref=e261]: Time Test Project u06g
                      - cell "Time Task u06g" [ref=e262]:
                        - paragraph [ref=e263]: Time Task u06g
                      - cell "Medium" [ref=e264]:
                        - generic [ref=e265]: Medium
                      - cell "—" [ref=e266]:
                        - generic [ref=e267]: —
                    - row "Task Test Project u022 Companion Task u022 Medium —" [ref=e268] [cursor=pointer]:
                      - cell "Task Test Project u022" [ref=e269]:
                        - generic [ref=e270]: Task Test Project u022
                      - cell "Companion Task u022" [ref=e271]:
                        - paragraph [ref=e272]: Companion Task u022
                      - cell "Medium" [ref=e273]:
                        - generic [ref=e274]: Medium
                      - cell "—" [ref=e275]:
                        - generic [ref=e276]: —
                    - row "Budget Test Project tz7k Budget Task tz7k Medium —" [ref=e277] [cursor=pointer]:
                      - cell "Budget Test Project tz7k" [ref=e278]:
                        - generic [ref=e279]: Budget Test Project tz7k
                      - cell "Budget Task tz7k" [ref=e280]:
                        - paragraph [ref=e281]: Budget Task tz7k
                      - cell "Medium" [ref=e282]:
                        - generic [ref=e283]: Medium
                      - cell "—" [ref=e284]:
                        - generic [ref=e285]: —
                    - row "Team spec mw6yqevrc73b Execution -- warrant / attachment Medium —" [ref=e286] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e287]:
                        - generic [ref=e288]: Team spec mw6yqevrc73b
                      - cell "Execution -- warrant / attachment" [ref=e289]:
                        - paragraph [ref=e290]: Execution -- warrant / attachment
                      - cell "Medium" [ref=e291]:
                        - generic [ref=e292]: Medium
                      - cell "—" [ref=e293]:
                        - generic [ref=e294]: —
                    - row "Team spec mw6yqevrc73b Post-judgment -- taxation of costs / appeal Medium —" [ref=e295] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e296]:
                        - generic [ref=e297]: Team spec mw6yqevrc73b
                      - cell "Post-judgment -- taxation of costs / appeal" [ref=e298]:
                        - paragraph [ref=e299]: Post-judgment -- taxation of costs / appeal
                      - cell "Medium" [ref=e300]:
                        - generic [ref=e301]: Medium
                      - cell "—" [ref=e302]:
                        - generic [ref=e303]: —
                    - row "Team spec mw6yqevrc73b Trial / hearing attendance Medium —" [ref=e304] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e305]:
                        - generic [ref=e306]: Team spec mw6yqevrc73b
                      - cell "Trial / hearing attendance" [ref=e307]:
                        - paragraph [ref=e308]: Trial / hearing attendance
                      - cell "Medium" [ref=e309]:
                        - generic [ref=e310]: Medium
                      - cell "—" [ref=e311]:
                        - generic [ref=e312]: —
                    - row "Team spec mw6yqevrc73b Pre-trial conference preparation Medium —" [ref=e313] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e314]:
                        - generic [ref=e315]: Team spec mw6yqevrc73b
                      - cell "Pre-trial conference preparation" [ref=e316]:
                        - paragraph [ref=e317]: Pre-trial conference preparation
                      - cell "Medium" [ref=e318]:
                        - generic [ref=e319]: Medium
                      - cell "—" [ref=e320]:
                        - generic [ref=e321]: —
                    - row "Team spec mw6yqevrc73b Discovery -- request & exchange documents Medium —" [ref=e322] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e323]:
                        - generic [ref=e324]: Team spec mw6yqevrc73b
                      - cell "Discovery -- request & exchange documents" [ref=e325]:
                        - paragraph [ref=e326]: Discovery -- request & exchange documents
                      - cell "Medium" [ref=e327]:
                        - generic [ref=e328]: Medium
                      - cell "—" [ref=e329]:
                        - generic [ref=e330]: —
                    - row "Team spec mw6yqevrc73b File plea / exception / counterclaim Medium —" [ref=e331] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e332]:
                        - generic [ref=e333]: Team spec mw6yqevrc73b
                      - cell "File plea / exception / counterclaim" [ref=e334]:
                        - paragraph [ref=e335]: File plea / exception / counterclaim
                      - cell "Medium" [ref=e336]:
                        - generic [ref=e337]: Medium
                      - cell "—" [ref=e338]:
                        - generic [ref=e339]: —
                    - row "Team spec mw6yqevrc73b Issue summons / combined summons Medium —" [ref=e340] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e341]:
                        - generic [ref=e342]: Team spec mw6yqevrc73b
                      - cell "Issue summons / combined summons" [ref=e343]:
                        - paragraph [ref=e344]: Issue summons / combined summons
                      - cell "Medium" [ref=e345]:
                        - generic [ref=e346]: Medium
                      - cell "—" [ref=e347]:
                        - generic [ref=e348]: —
                    - row "Team spec mw6yqevrc73b Letter of demand Medium —" [ref=e349] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e350]:
                        - generic [ref=e351]: Team spec mw6yqevrc73b
                      - cell "Letter of demand" [ref=e352]:
                        - paragraph [ref=e353]: Letter of demand
                      - cell "Medium" [ref=e354]:
                        - generic [ref=e355]: Medium
                      - cell "—" [ref=e356]:
                        - generic [ref=e357]: —
                    - row "Team spec mw6yqevrc73b Initial consultation & case assessment Medium —" [ref=e358] [cursor=pointer]:
                      - cell "Team spec mw6yqevrc73b" [ref=e359]:
                        - generic [ref=e360]: Team spec mw6yqevrc73b
                      - cell "Initial consultation & case assessment" [ref=e361]:
                        - paragraph [ref=e362]: Initial consultation & case assessment
                      - cell "Medium" [ref=e363]:
                        - generic [ref=e364]: Medium
                      - cell "—" [ref=e365]:
                        - generic [ref=e366]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Execution -- warrant / attachment Medium —" [ref=e367] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e368]:
                        - generic [ref=e369]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Execution -- warrant / attachment" [ref=e370]:
                        - paragraph [ref=e371]: Execution -- warrant / attachment
                      - cell "Medium" [ref=e372]:
                        - generic [ref=e373]: Medium
                      - cell "—" [ref=e374]:
                        - generic [ref=e375]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Post-judgment -- taxation of costs / appeal Medium —" [ref=e376] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e377]:
                        - generic [ref=e378]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Post-judgment -- taxation of costs / appeal" [ref=e379]:
                        - paragraph [ref=e380]: Post-judgment -- taxation of costs / appeal
                      - cell "Medium" [ref=e381]:
                        - generic [ref=e382]: Medium
                      - cell "—" [ref=e383]:
                        - generic [ref=e384]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Trial / hearing attendance Medium —" [ref=e385] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e386]:
                        - generic [ref=e387]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Trial / hearing attendance" [ref=e388]:
                        - paragraph [ref=e389]: Trial / hearing attendance
                      - cell "Medium" [ref=e390]:
                        - generic [ref=e391]: Medium
                      - cell "—" [ref=e392]:
                        - generic [ref=e393]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Pre-trial conference preparation Medium —" [ref=e394] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e395]:
                        - generic [ref=e396]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Pre-trial conference preparation" [ref=e397]:
                        - paragraph [ref=e398]: Pre-trial conference preparation
                      - cell "Medium" [ref=e399]:
                        - generic [ref=e400]: Medium
                      - cell "—" [ref=e401]:
                        - generic [ref=e402]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Discovery -- request & exchange documents Medium —" [ref=e403] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e404]:
                        - generic [ref=e405]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Discovery -- request & exchange documents" [ref=e406]:
                        - paragraph [ref=e407]: Discovery -- request & exchange documents
                      - cell "Medium" [ref=e408]:
                        - generic [ref=e409]: Medium
                      - cell "—" [ref=e410]:
                        - generic [ref=e411]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa File plea / exception / counterclaim Medium —" [ref=e412] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e413]:
                        - generic [ref=e414]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "File plea / exception / counterclaim" [ref=e415]:
                        - paragraph [ref=e416]: File plea / exception / counterclaim
                      - cell "Medium" [ref=e417]:
                        - generic [ref=e418]: Medium
                      - cell "—" [ref=e419]:
                        - generic [ref=e420]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Issue summons / combined summons Medium —" [ref=e421] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e422]:
                        - generic [ref=e423]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Issue summons / combined summons" [ref=e424]:
                        - paragraph [ref=e425]: Issue summons / combined summons
                      - cell "Medium" [ref=e426]:
                        - generic [ref=e427]: Medium
                      - cell "—" [ref=e428]:
                        - generic [ref=e429]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Letter of demand Medium —" [ref=e430] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e431]:
                        - generic [ref=e432]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Letter of demand" [ref=e433]:
                        - paragraph [ref=e434]: Letter of demand
                      - cell "Medium" [ref=e435]:
                        - generic [ref=e436]: Medium
                      - cell "—" [ref=e437]:
                        - generic [ref=e438]: —
                    - row "Ndlovu v RAF mw6yn5mk4aaa Initial consultation & case assessment Medium —" [ref=e439] [cursor=pointer]:
                      - cell "Ndlovu v RAF mw6yn5mk4aaa" [ref=e440]:
                        - generic [ref=e441]: Ndlovu v RAF mw6yn5mk4aaa
                      - cell "Initial consultation & case assessment" [ref=e442]:
                        - paragraph [ref=e443]: Initial consultation & case assessment
                      - cell "Medium" [ref=e444]:
                        - generic [ref=e445]: Medium
                      - cell "—" [ref=e446]:
                        - generic [ref=e447]: —
            - generic [ref=e448]:
              - generic [ref=e450]:
                - heading "Time Breakdown" [level=3] [ref=e451]
                - generic [ref=e453]:
                  - generic [ref=e455]:
                    - list [ref=e457]:
                      - listitem [ref=e458]:
                        - img "Ndlovu v RAF mw6yn5mk4aaa legend icon" [ref=e459]
                        - text: Ndlovu v RAF mw6yn5mk4aaa
                    - application [ref=e461]
                  - generic:
                    - generic: 2.3h
                    - generic: total
                - generic [ref=e468]:
                  - generic [ref=e471]: Ndlovu v RAF mw6yn5mk4aaa
                  - generic [ref=e472]: 2.3h (100%)
              - generic [ref=e473]:
                - generic [ref=e474]:
                  - heading "Today" [level=2] [ref=e475]
                  - generic [ref=e476]: 2h 15m
                - generic [ref=e477]:
                  - generic [ref=e478]:
                    - generic [ref=e479]:
                      - paragraph [ref=e480]: Letter of demand
                      - paragraph [ref=e481]: Ndlovu v RAF mw6yn5mk4aaa — Collate medical records for the letter of demand
                    - generic [ref=e482]:
                      - generic [ref=e483]: Billable
                      - generic [ref=e484]: 45m
                  - generic [ref=e485]:
                    - generic [ref=e486]:
                      - paragraph [ref=e487]: Initial consultation & case assessment
                      - paragraph [ref=e488]: Ndlovu v RAF mw6yn5mk4aaa — Research case law — personal injury precedents
                    - generic [ref=e489]:
                      - generic [ref=e490]: Billable
                      - generic [ref=e491]: 1h 30m
          - generic [ref=e493]:
            - generic [ref=e494]:
              - heading "This Week" [level=2] [ref=e495]
              - generic [ref=e496]:
                - button "Previous week" [ref=e497]:
                  - img
                - generic [ref=e498]: Nov 2 – Nov 8, 2026
                - button "Next week" [ref=e499]:
                  - img
            - generic [ref=e500]:
              - generic [ref=e501]:
                - generic [ref=e502]:
                  - paragraph [ref=e503]: Total
                  - paragraph [ref=e504]: 5h 45m
                - generic [ref=e505]:
                  - generic [ref=e506]:
                    - paragraph [ref=e507]: Billable
                    - paragraph [ref=e508]: 5h 15m
                  - generic [ref=e509]:
                    - paragraph [ref=e510]: Non-billable
                    - paragraph [ref=e511]: 30m
              - generic [ref=e512]:
                - heading "By Matter" [level=3] [ref=e513]
                - generic [ref=e514]:
                  - generic [ref=e515]:
                    - generic [ref=e516]: Ndlovu v RAF mw6yn5mk4aaa
                    - generic [ref=e517]:
                      - generic [ref=e518]: 2h 15m
                      - generic [ref=e519]: 2h 15m
                  - generic [ref=e520]:
                    - generic [ref=e521]: Budget Test Project tz7k
                    - generic [ref=e522]:
                      - generic [ref=e523]: 2h
                      - generic [ref=e524]: 2h
                  - generic [ref=e525]:
                    - generic [ref=e526]: Time Test Project u06g
                    - generic [ref=e527]:
                      - generic [ref=e528]: 1h
                      - generic [ref=e529]: 1h 30m
  - generic [ref=e530]:
    - heading "Command Palette" [level=2] [ref=e531]
    - paragraph [ref=e532]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e533]
```

# Test source

```ts
  147 |   test("3. Delete time entry via API and verify", async () => {
  148 |     const { status } = await apiStatus(
  149 |       `/api/time-entries/${timeEntryId}`,
  150 |       { method: "DELETE" },
  151 |       "carol"
  152 |     );
  153 |     expect(status).toBe(204);
  154 | 
  155 |     // Verify entry is gone via API
  156 |     const entries = await apiOk<
  157 |       Array<{
  158 |         id: string;
  159 |       }>
  160 |     >(`/api/tasks/${taskId}/time-entries`, {}, "carol");
  161 |     const stillExists = entries.find((e) => e.id === timeEntryId);
  162 |     expect(stillExists).toBeFalsy();
  163 |   });
  164 | 
  165 |   test("4. Time entry captures rate snapshot", async () => {
  166 |     // Log a time entry via API and check that the rate snapshot fields are populated
  167 |     const { status, data } = await apiStatus(
  168 |       `/api/tasks/${taskId}/time-entries`,
  169 |       {
  170 |         method: "POST",
  171 |         body: {
  172 |           date: new Date().toISOString().split("T")[0],
  173 |           durationMinutes: 60,
  174 |           billable: true,
  175 |           description: `Rate snapshot test ${RUN_ID}`,
  176 |         },
  177 |       },
  178 |       "carol"
  179 |     );
  180 | 
  181 |     expect(status).toBe(201);
  182 | 
  183 |     // The entry carries the rate snapshot fields (null when no rate is configured)
  184 |     expect(data).toHaveProperty("billingRateSnapshot");
  185 |     expect(data).toHaveProperty("billingRateCurrency");
  186 | 
  187 |     timeEntryId = data.id;
  188 |   });
  189 | 
  190 |   test("5. Billable flag defaults to checked in UI", async ({ page }) => {
  191 |     await loginAs(page, "carol");
  192 |     const row = await openTaskRow(page, projectId, TASK_TITLE);
  193 | 
  194 |     await row.getByRole("button", { name: "Log Time" }).click();
  195 |     const dialog = page.getByRole("dialog", { name: "Log Time" });
  196 |     await expect(dialog).toBeVisible();
  197 |     await expect(dialog.getByRole("checkbox", { name: "Billable" })).toBeChecked();
  198 | 
  199 |     // Close dialog without submitting
  200 |     await page.keyboard.press("Escape");
  201 |     await expect(dialog).toBeHidden();
  202 |   });
  203 | 
  204 |   test("6. Mark time entry non-billable", async () => {
  205 |     // Create a non-billable entry via API
  206 |     const { status, data } = await apiStatus(
  207 |       `/api/tasks/${taskId}/time-entries`,
  208 |       {
  209 |         method: "POST",
  210 |         body: {
  211 |           date: new Date().toISOString().split("T")[0],
  212 |           durationMinutes: 30,
  213 |           billable: false,
  214 |           description: `Non-billable entry ${RUN_ID}`,
  215 |         },
  216 |       },
  217 |       "carol"
  218 |     );
  219 | 
  220 |     expect(status).toBe(201);
  221 |     expect(data.billable).toBe(false);
  222 | 
  223 |     // Verify the entry is non-billable
  224 |     const entries = await apiOk<
  225 |       Array<{
  226 |         id: string;
  227 |         billable: boolean;
  228 |         description: string;
  229 |       }>
  230 |     >(`/api/tasks/${taskId}/time-entries`, {}, "carol");
  231 |     const nonBillable = entries.find((e) =>
  232 |       e.description?.includes(`Non-billable entry ${RUN_ID}`)
  233 |     );
  234 |     expect(nonBillable).toBeTruthy();
  235 |     expect(nonBillable!.billable).toBe(false);
  236 |   });
  237 | 
  238 |   test("7. My Work lists today's time entries", async ({ page }) => {
  239 |     // Carol logged the billable (test 4) and non-billable (test 6) entries today
  240 |     await loginAs(page, "carol");
  241 |     await page.goto(`${BASE}/my-work`);
  242 |     await expect(page.getByRole("heading", { level: 1, name: "My Work" })).toBeVisible({
  243 |       timeout: 10_000,
  244 |     });
  245 |     await expect(
  246 |       page.getByText(`${PROJECT_NAME} \u2014 Non-billable entry ${RUN_ID}`)
> 247 |     ).toBeVisible();
      |       ^ Error: expect(locator).toBeVisible() failed
  248 |     await expect(
  249 |       page.getByText(`${PROJECT_NAME} \u2014 Rate snapshot test ${RUN_ID}`)
  250 |     ).toBeVisible();
  251 |   });
  252 | });
  253 | 
```