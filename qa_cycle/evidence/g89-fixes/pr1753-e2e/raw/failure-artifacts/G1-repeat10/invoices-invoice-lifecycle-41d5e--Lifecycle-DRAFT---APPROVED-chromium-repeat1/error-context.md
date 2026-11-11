# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: invoices/invoice-lifecycle.spec.ts >> INV-02: Invoice Lifecycle >> DRAFT -> APPROVED
- Location: e2e/tests/invoices/invoice-lifecycle.spec.ts:88:7

# Error details

```
Error: POST /api/clients "InvLife Client p84w"

expect(received).toBe(expected) // Object.is equality

Expected: 201
Received: 409
```

# Test source

```ts
  55  | /**
  56  |  * A backend call whose status the caller asserts (a specific 2xx, or a refusal); returns the status
  57  |  * and the parsed JSON body (null when empty or not JSON).
  58  |  */
  59  | export async function apiStatus<T = ApiEntity>(
  60  |   path: string,
  61  |   init: ApiInit = {},
  62  |   user: SeedUser = "alice"
  63  | ): Promise<{ status: number; data: T }> {
  64  |   const res = await api(path, init, user);
  65  |   const text = await res.text();
  66  |   let data = null as T;
  67  |   try {
  68  |     data = (text ? JSON.parse(text) : null) as T;
  69  |   } catch {
  70  |     // A non-JSON error body: the caller asserts on the status
  71  |   }
  72  |   return { status: res.status, data };
  73  | }
  74  | 
  75  | export interface Member {
  76  |   id: string;
  77  |   name: string;
  78  |   email: string;
  79  | }
  80  | 
  81  | /** A seed member (Alice / Bob / Carol) by first name. */
  82  | export async function memberNamed(first: "Alice" | "Bob" | "Carol"): Promise<Member> {
  83  |   const members = await apiOk<Member[]>("/api/members");
  84  |   const hit = members.find((m) => m.name?.startsWith(first));
  85  |   expect(hit, `seed member ${first}`).toBeDefined();
  86  |   return hit!;
  87  | }
  88  | 
  89  | /**
  90  |  * Settles every onboarding checklist item: skip it when it needs a linked document or depends on a
  91  |  * skipped item, otherwise complete it; any other refusal fails loudly. Settling the last item
  92  |  * auto-activates a client whose activation prerequisites (billing address) are on file.
  93  |  */
  94  | export async function settleChecklists(clientId: string): Promise<void> {
  95  |   const checklists = await apiOk<
  96  |     {
  97  |       items?: {
  98  |         id: string;
  99  |         status: string;
  100 |         requiresDocument: boolean;
  101 |         dependsOnItemId: string | null;
  102 |       }[];
  103 |     }[]
  104 |   >(`/api/clients/${clientId}/checklists`);
  105 |   expect(checklists.length, "an onboarding checklist was instantiated").toBeGreaterThan(0);
  106 |   for (const checklist of checklists) {
  107 |     const items = checklist.items ?? [];
  108 |     const skipped = new Set(items.filter((i) => i.status === "SKIPPED").map((i) => i.id));
  109 |     for (const item of items) {
  110 |       if (item.status === "COMPLETED" || item.status === "SKIPPED") continue;
  111 |       // A dependent of a skipped item can't be completed (409 "Checklist item blocked"), so it is
  112 |       // skipped too.
  113 |       if (item.requiresDocument || (item.dependsOnItemId && skipped.has(item.dependsOnItemId))) {
  114 |         skipped.add(item.id);
  115 |         await apiOk(`/api/checklist-items/${item.id}/skip`, {
  116 |           method: "PUT",
  117 |           body: { reason: "E2E: no document to link" },
  118 |         });
  119 |       } else {
  120 |         await apiOk(`/api/checklist-items/${item.id}/complete`, {
  121 |           method: "PUT",
  122 |           body: { notes: "E2E auto-complete" },
  123 |         });
  124 |       }
  125 |     }
  126 |   }
  127 | }
  128 | 
  129 | export interface CreatedClient {
  130 |   id: string;
  131 |   name: string;
  132 |   lifecycleStatus: string;
  133 | }
  134 | 
  135 | /**
  136 |  * A fresh PROSPECT individual client. With a billing address by default — LIFECYCLE_ACTIVATION and
  137 |  * fee notes need address line 1 / city / country; `withAddress: false` leaves it off.
  138 |  */
  139 | export async function createClient(
  140 |   name: string,
  141 |   email: string,
  142 |   { withAddress = true }: { withAddress?: boolean } = {}
  143 | ): Promise<CreatedClient> {
  144 |   const { status, data } = await apiStatus<CreatedClient>("/api/clients", {
  145 |     method: "POST",
  146 |     body: {
  147 |       name,
  148 |       email,
  149 |       phone: "+27-11-555-0199",
  150 |       ...(withAddress
  151 |         ? { addressLine1: "12 Rivonia Road", city: "Sandton", postalCode: "2196", country: "ZA" }
  152 |         : {}),
  153 |     },
  154 |   });
> 155 |   expect(status, `POST /api/clients "${name}"`).toBe(201);
      |                                                 ^ Error: POST /api/clients "InvLife Client p84w"
  156 |   return data;
  157 | }
  158 | 
  159 | /** A client's lifecycle status, read from the API. */
  160 | export async function clientStatus(clientId: string): Promise<string> {
  161 |   return (await apiOk<{ lifecycleStatus: string }>(`/api/clients/${clientId}`)).lifecycleStatus;
  162 | }
  163 | 
  164 | /**
  165 |  * A fresh ACTIVE individual client with a billing address (fee notes need one) and an
  166 |  * auto-provisioned portal contact for `email`: PROSPECT → ONBOARDING → checklist auto-activation.
  167 |  */
  168 | export async function createActiveClient(name: string, email: string): Promise<string> {
  169 |   const client = await createClient(name, email);
  170 |   await apiOk(`/api/clients/${client.id}/transition`, {
  171 |     method: "POST",
  172 |     body: { targetStatus: "ONBOARDING", notes: "E2E setup" },
  173 |   });
  174 |   await settleChecklists(client.id);
  175 |   await expect.poll(() => clientStatus(client.id)).toBe("ACTIVE");
  176 |   return client.id;
  177 | }
  178 | 
  179 | /** A fresh matter (optionally for a client); returns its id. */
  180 | export async function createMatter(name: string, clientId?: string): Promise<string> {
  181 |   const matter = await apiOk<{ id: string }>("/api/matters", {
  182 |     method: "POST",
  183 |     body: { name, description: "E2E setup", ...(clientId ? { clientId } : {}) },
  184 |   });
  185 |   return matter.id;
  186 | }
  187 | 
  188 | /** Adds a member to a matter as a contributor. */
  189 | export async function addMatterMember(matterId: string, memberId: string): Promise<void> {
  190 |   await apiOk(`/api/matters/${matterId}/members`, {
  191 |     method: "POST",
  192 |     body: { memberId, projectRole: "contributor" },
  193 |   });
  194 | }
  195 | 
  196 | /** A fresh task on a matter; returns its id. */
  197 | export async function createTask(matterId: string, title: string): Promise<string> {
  198 |   const task = await apiOk<{ id: string }>(`/api/matters/${matterId}/tasks`, {
  199 |     method: "POST",
  200 |     body: { title },
  201 |   });
  202 |   return task.id;
  203 | }
  204 | 
  205 | /** A matter-scoped ZAR hourly billing rate for a member, effective from the 1st of this month. */
  206 | export async function createMatterRate(
  207 |   matterId: string,
  208 |   memberId: string,
  209 |   hourlyRate: number
  210 | ): Promise<void> {
  211 |   await apiOk("/api/billing-rates", {
  212 |     method: "POST",
  213 |     body: {
  214 |       memberId,
  215 |       matterId,
  216 |       currency: "ZAR",
  217 |       hourlyRate,
  218 |       effectiveFrom: `${todayInSA().slice(0, 8)}01`,
  219 |     },
  220 |   });
  221 | }
  222 | 
  223 | /** Logs billable time on a task as `user`; returns the time entry id. */
  224 | export async function logTime(
  225 |   taskId: string,
  226 |   minutes: number,
  227 |   description: string,
  228 |   user: SeedUser = "alice"
  229 | ): Promise<string> {
  230 |   const entry = await apiOk<{ id: string }>(
  231 |     `/api/tasks/${taskId}/time-entries`,
  232 |     {
  233 |       method: "POST",
  234 |       body: { date: todayInSA(), durationMinutes: minutes, billable: true, description },
  235 |     },
  236 |     user
  237 |   );
  238 |   return entry.id;
  239 | }
  240 | 
  241 | export interface FeeNoteLine {
  242 |   id: string;
  243 |   description: string;
  244 |   amount: number;
  245 |   unitPrice: number;
  246 | }
  247 | 
  248 | export interface FeeNote {
  249 |   id: string;
  250 |   status: string;
  251 |   subtotal: number;
  252 |   total: number;
  253 |   lines: FeeNoteLine[];
  254 | }
  255 | 
```