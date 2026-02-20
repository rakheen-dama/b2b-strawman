# DocTeams Product Walkthrough

> A hands-on guide to exercising the full DocTeams platform through the story of **Apex Accounting** — a 5-person firm setting up and running their practice.

## Prerequisites

```bash
# Terminal 1: Start infrastructure
cd compose && docker compose up -d

# Terminal 2: Start backend
cd backend && ./mvnw spring-boot:run

# Terminal 3: Start frontend
cd frontend && pnpm dev
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- You need a Clerk dev instance configured (sign-up, create org, invite members)

---

## Part 1: The Narrative — A Week at Apex Accounting

### Chapter 1: First Login & Org Setup

**The story**: Sarah Chen is the managing partner at Apex Accounting. She's evaluating DocTeams to replace their spreadsheet-and-email workflow.

#### 1.1 Sign Up & Create Organization

1. Navigate to **http://localhost:3000**
2. Click **"Get Started"** on the landing page
3. Sign up with an email (this becomes Sarah, the org owner)
4. After sign-up, you'll land on the **Dashboard** page — it prompts you to create an organization
5. Click **"Create Organization"** → name it **"Apex Accounting"**
6. You're now inside the org at `/org/apex-accounting/dashboard`

**What you should see**: An empty dashboard with zero KPIs. The sidebar shows all nav items. This is the "blank canvas" state.

> **Domain context**: In a real accounting firm, the owner would do this initial setup, then invite their team. The org name appears in invoices, documents, and the client portal.

#### 1.2 Invite Team Members

Sarah needs to invite her team. In a real firm, these would be:
- **James Park** — Senior Accountant (admin role)
- **Priya Naidoo** — Junior Accountant (member role)
- **Tom Wilson** — Bookkeeper (member role)

1. Click the **Clerk OrganizationSwitcher** in the header (top-left area)
2. Go to **Organization Settings** → **Members** → **Invite**
3. Invite 2-3 email addresses with appropriate roles:
   - One as **Admin** (James)
   - One or two as **Member** (Priya, Tom)

> **Tip**: Use `+` aliases for testing: `you+james@gmail.com`, `you+priya@gmail.com`. Each needs to accept the invite and sign in separately to appear as a team member.

4. After members accept invites, navigate to **Team** in the sidebar — you should see all members listed with their roles.

> **What's happening under the hood**: When a member joins via Clerk, a webhook fires → Next.js receives it → calls `/internal/members/sync` → backend creates a `Member` record in the tenant schema. The member's Clerk user ID, name, and email are stored locally for fast lookups.

#### 1.3 Configure Org Settings

Before doing any real work, Sarah configures the firm's defaults.

**Navigate to**: Sidebar → **Settings**

You'll see a grid of settings cards. Work through these:

##### Billing Rates (Settings → Rates)
This is the org's default rate card — what each team member's time is worth.

1. Click **"Add Rate"**
2. Create rates for each member:
   - Sarah Chen: **R1,500/hr** (partner rate)
   - James Park: **R900/hr** (senior rate)
   - Priya Naidoo: **R500/hr** (junior rate)
   - Tom Wilson: **R400/hr** (bookkeeper rate)
3. Set the **Default Currency** to **ZAR** (South African Rand)

> **Domain context**: Rate cards are the foundation of the revenue engine. Every time entry captures a "snapshot" of the applicable rate at the time of logging. The 3-level hierarchy (org → project → customer) lets you override rates for specific engagements without changing the defaults.

##### Cost Rates (Settings → Rates, scroll down)
Cost rates represent what each team member actually costs the firm (salary + overhead).

1. Add cost rates:
   - Sarah: **R800/hr**
   - James: **R500/hr**
   - Priya: **R250/hr**
   - Tom: **R200/hr**

> **Why this matters**: Profitability = Revenue (billing rate × hours) minus Cost (cost rate × hours). Without cost rates, you can see revenue but not margins.

##### Branding (Settings → Templates → Branding section)
1. Upload a logo (any small PNG/JPG will do)
2. Set brand color (e.g., `#1a5276`)
3. Add footer text: "Apex Accounting (Pty) Ltd | Reg: 2020/123456/07"

> **Where this appears**: Invoice PDFs, generated documents, and eventually the client portal.

##### Custom Fields (Settings → Custom Fields)
These let you track firm-specific data on projects, tasks, and customers.

1. Click **"Add Field"** for the **Customer** entity type
2. Create a few useful fields:
   - **"Tax Number"** (type: Text)
   - **"FICA Status"** (type: Dropdown, options: Pending, Verified, Expired)
   - **"Annual Revenue"** (type: Number)
3. Create a **Field Group** called "Client Info" and add these fields to it

> **Domain context**: Every professional services vertical has entity-specific metadata. Accounting firms track tax numbers and FICA status. Law firms would track matter numbers and court jurisdictions. Custom fields make the platform adaptable without code changes.

##### Tags (Settings → Tags)
1. Create a few tags:
   - **"VIP"** (for high-value clients)
   - **"Monthly"** (for recurring engagement type)
   - **"Annual"** (for annual audits/reviews)
   - **"Tax"** (for tax-specific work)

---

### Chapter 2: Onboarding Your First Client

**The story**: Apex Accounting just signed a new client — **GreenTech Solutions**, a small tech company that needs monthly bookkeeping and an annual financial review.

#### 2.1 Create the Customer

1. Navigate to **Customers** in the sidebar
2. Click **"Add Customer"**
3. Fill in:
   - Name: **GreenTech Solutions**
   - Contact Name: **Lisa Moyo**
   - Contact Email: **lisa@greentech.example.com**
   - Phone: **+27 11 555 0123**
4. After creation, you land on the customer detail page

**Explore the customer detail page**: Notice the tabs — Projects, Documents, Invoices, Rates, Generated Docs, Financials, and Onboarding. Most are empty. The **Setup Guidance** cards on the Overview tell you what's missing.

#### 2.2 Set Custom Field Values

1. On the customer detail page, look for the custom fields section
2. Fill in:
   - Tax Number: **9876543210**
   - FICA Status: **Pending**
   - Annual Revenue: **5000000**

#### 2.3 Add Tags

1. On the customer detail page, add tags: **"VIP"**, **"Monthly"**

#### 2.4 Start the Onboarding Process

1. Notice the **lifecycle status** indicator — the customer starts as **PROSPECT**
2. Click the lifecycle dropdown → **"Move to Onboarding"**
3. The status changes to **ONBOARDING** and the **Onboarding tab** appears

> **Domain context**: Customer lifecycle (Prospect → Onboarding → Active → Offboarded) tracks where each client is in your pipeline. The onboarding stage triggers compliance checklists.

#### 2.5 Attach a Compliance Checklist

If compliance pack seeding ran (it does automatically), you should have checklist templates available.

1. Go to the **Onboarding** tab
2. Click **"Start Checklist"** and select a template (e.g., "FICA Compliance")
3. You'll see checklist items like:
   - Collect ID document
   - Verify proof of address
   - Confirm source of funds
4. Mark a few items as **Complete** by clicking them
5. Skip one with a reason: "Client will provide next week"

> **Domain context**: In South Africa, FICA (Financial Intelligence Centre Act) requires accounting and law firms to verify client identity before engaging. This is not optional — firms face penalties for non-compliance. The checklist engine automates this paper trail.

#### 2.6 Upload a Client Document

1. Go to the **Documents** tab on the customer detail page
2. Upload a file (any PDF or image — pretend it's a FICA ID document)
3. After upload, click **"Confirm"** to finalize

#### 2.7 Complete Onboarding

1. Once enough checklist items are done, transition the customer: **Onboarding → Active**
2. The customer is now fully onboarded and ready for project work

---

### Chapter 3: Setting Up an Engagement

**The story**: GreenTech needs monthly bookkeeping. Sarah assigns James as the lead, with Priya doing the day-to-day work.

#### 3.1 Create a Project

1. Navigate to **Projects** in the sidebar
2. Click **"New Project"**
3. Fill in:
   - Name: **"GreenTech Monthly Bookkeeping — Feb 2026"**
   - Description: **"Monthly bookkeeping and reconciliation for GreenTech Solutions"**
4. You land on the project detail page

**Explore the project detail page**: Tabs for Overview, Documents, Customers, Members, Tasks, Time, Budget, Financials, Rates, Generated Docs, Activity. The **Overview tab** shows setup guidance — what's missing to make this project operational.

#### 3.2 Link the Customer

1. Go to the **Customers** tab
2. Click **"Link Customer"** → select **GreenTech Solutions**

> **Why linking matters**: This connects the project to a billable customer. Without this link, time tracked on this project can't be invoiced. The setup guidance card will flag this if you forget.

#### 3.3 Add Team Members

1. Go to the **Members** tab
2. Click **"Add Member"** → add **James Park** and **Priya Naidoo**
3. Transfer the **Project Lead** role to James (click the role indicator next to his name)

> **Domain context**: The project lead has elevated permissions (edit project, manage budget, view financials) without needing org-level admin. This maps to how firms work — a senior accountant runs the engagement but doesn't need access to firm-wide settings.

#### 3.4 Create Tasks

1. Go to the **Tasks** tab
2. Create several tasks that represent the monthly bookkeeping workflow:

| Task | Priority | Assignee |
|------|----------|----------|
| Collect bank statements from client | Medium | Priya |
| Reconcile bank accounts | High | Priya |
| Process supplier invoices | Medium | Priya |
| Review and post journal entries | High | James |
| Prepare monthly management report | High | James |
| Client review meeting | Low | James |

3. For each task, set the **priority** and **assignee**

> **Tip**: You can also leave tasks unassigned and let team members **"Claim"** them — useful for firms where juniors pick up available work.

#### 3.5 Set a Budget

1. Go to the **Budget** tab
2. Click **"Configure Budget"**
3. Set:
   - Hours budget: **40 hours**
   - Amount budget: **R25,000** (ZAR)
   - Alert threshold: **80%**

> **Domain context**: Budget tracking prevents scope creep — the #1 profitability killer in professional services. The 80% alert triggers a notification so the project lead can take action before the budget is blown.

#### 3.6 Set Project-Specific Rates (Optional)

If GreenTech has a negotiated discount:

1. Go to the **Rates** tab
2. Add a project-level rate override:
   - James Park: **R800/hr** (discounted from R900)
   - Priya Naidoo: **R450/hr** (discounted from R500)

> **How rate hierarchy works**: When Priya logs time on this project, the system checks: project rate (R450) → customer rate (none set) → org rate (R500). It uses the most specific match. The rate is "snapshotted" onto each time entry at creation time.

---

### Chapter 4: Doing the Work

**The story**: It's a typical workday. The team is working on GreenTech's bookkeeping.

#### 4.1 Log Time (as Priya)

Sign in as Priya (or stay as Sarah and imagine you're Priya — the member dropdown in time logging lets you select any member if you're admin).

1. Navigate to **Projects** → **GreenTech Monthly Bookkeeping**
2. Go to the **Tasks** tab
3. Click on **"Reconcile bank accounts"**
4. Click **"Log Time"**
5. Fill in:
   - Date: **today**
   - Duration: **2.5 hours** (or 150 minutes)
   - Notes: "Reconciled Standard Bank cheque account, 47 transactions"
6. The entry appears in the task's time list with the snapshotted billing rate

Repeat for a few more tasks to build up data:
- "Collect bank statements" — 0.5 hours
- "Process supplier invoices" — 3 hours

#### 4.2 Check "My Work" Page

1. Navigate to **My Work** in the sidebar
2. You'll see:
   - **Personal KPIs**: Hours logged this week, utilization rate
   - **Assigned tasks** across all projects
   - **Today's time entries**
   - **Weekly time summary**

> **Domain context**: "My Work" is the individual's home base — it answers "what should I work on?" and "how much have I billed this week?" without navigating project by project. Essential for firms with team members working across multiple clients.

#### 4.3 Add Comments

1. Go back to the project, open a task
2. In the **Comments** section below the task, add a comment:
   - "Bank statement for January is missing — emailed Lisa to follow up"
3. Notice the comment appears with your name and timestamp

> **Visibility note**: Comments can be **Internal** (only visible to the team) or **Shared** (visible in the client portal too). Internal is the default — toggle visibility if you want the client to see it.

#### 4.4 Upload a Work Document

1. Go to the project's **Documents** tab
2. Upload a file (pretend it's the monthly reconciliation report)
3. Confirm the upload
4. Optionally toggle visibility to **Shared** if the client should see it in the portal

#### 4.5 Check Notifications

1. Click the **bell icon** in the header
2. You should see notifications for:
   - Task assignments
   - Comments on tasks you're involved with
   - Budget alerts (if you've hit the threshold)
3. Navigate to **Notifications** page for the full inbox
4. Check **Settings → Notifications** to see preference controls (per-type, in-app/email toggles)

---

### Chapter 5: The Revenue Cycle

**The story**: End of the month. Time to bill GreenTech for the work done.

#### 5.1 Review Time Summary

1. Go to the project → **Time** tab
2. Review the time summary:
   - Total hours logged
   - Billable vs. non-billable breakdown
   - By-member breakdown
   - By-task breakdown

#### 5.2 Check Unbilled Time

1. Go to the project → **Overview** tab
2. Look for the **"Unbilled Time"** action card — it shows how much unbilled time exists
3. Or navigate to **Invoices** in the sidebar

#### 5.3 Generate an Invoice

1. Navigate to **Invoices** in the sidebar
2. Click **"New Invoice"** (or use the "Generate Invoice" action from the project/customer)
3. In the invoice generation dialog:
   - Select customer: **GreenTech Solutions**
   - Select project: **GreenTech Monthly Bookkeeping**
   - Date range: **this month**
   - The system shows all unbilled time entries with rates
4. Review the line items — each time entry becomes an invoice line
5. Click **"Generate"** — this creates a **DRAFT** invoice

#### 5.4 Invoice Lifecycle

1. Click into the draft invoice
2. Review:
   - Line items (from time entries)
   - Total amount
   - Customer details
3. Walk through the lifecycle:
   - **Approve**: DRAFT → APPROVED (internal sign-off)
   - **Send**: APPROVED → SENT (marks as delivered to client)
   - **Record Payment**: SENT → PAID (when payment received)

> **Domain context**: The draft stage is important — it lets the partner review before anything goes to the client. Many firms have a "partner review" step where a senior reviews all invoices before they're sent. The APPROVED → SENT distinction captures this.

#### 5.5 Preview / Generate PDF

1. On the invoice detail page, click **"Preview"** to see the HTML rendering
2. Use **"Generate Document"** dropdown to create a PDF
   - The PDF uses your org branding (logo, colors, footer)
   - It's saved as a generated document and stored in S3

#### 5.6 Check Profitability

1. Navigate to **Profitability** in the sidebar
2. Explore:
   - **Utilization table**: Hours billed vs. available per team member
   - **Project profitability**: Revenue vs. cost for each project
   - **Customer profitability**: Lifetime value per customer

3. Go to the project → **Financials** tab for project-specific P&L
4. Go to the customer → **Financials** tab for customer-level view

> **Domain context**: This is the "aha moment" for firm owners. Most small practices have no idea which clients are profitable and which are money-losers. The data was always there (time × rates - costs) but never surfaced. This visibility changes how firms price, staff, and prioritize work.

---

### Chapter 6: Recurring Work & Retainers

**The story**: GreenTech wants to move to a retainer arrangement — fixed monthly fee for ongoing bookkeeping.

#### 6.1 Save Project as Template

Before setting up the retainer, capture the current project structure as a reusable template.

1. Go to the project detail page
2. Click **"Save as Template"** (top-right action)
3. Name it: **"Monthly Bookkeeping Template"**
4. The template captures: tasks, assignments (by role), tags, and structure

#### 6.2 Browse Project Templates

1. Navigate to **Settings → Project Templates**
2. You should see the template you just saved
3. Click into it to review:
   - Template tasks with role-based assignments
   - Name tokens (you can use `{customer}`, `{month}`, `{year}` in the project name)

#### 6.3 Create a Recurring Schedule

1. Navigate to **Recurring Schedules** in the sidebar
2. Click **"New Schedule"**
3. Configure:
   - Template: **Monthly Bookkeeping Template**
   - Customer: **GreenTech Solutions**
   - Frequency: **Monthly**
   - Project name pattern: **"{customer} Bookkeeping — {month} {year}"**
   - Start date: **next month**
4. The schedule will automatically create a new project each month from the template

> **Domain context**: This eliminates the "create a new project every month" drudgery. For a firm with 50 monthly bookkeeping clients, that's 50 manual project setups avoided. The scheduler runs daily and creates projects when due.

#### 6.4 Set Up a Retainer Agreement

1. Navigate to **Retainers** in the sidebar
2. Click **"New Retainer"**
3. Configure:
   - Customer: **GreenTech Solutions**
   - Included hours: **40 hours/month**
   - Monthly fee: **R20,000**
   - Period: **Monthly**
   - Rollover policy: **Carry Forward** (unused hours roll to next month)
   - Start date: **this month**
4. The retainer creates its first **billing period** automatically

#### 6.5 Track Retainer Consumption

1. As team members log time on GreenTech projects, the retainer tracks consumption
2. Go to the retainer detail page to see:
   - Hours used vs. included
   - Percentage consumed
   - Remaining hours
3. At **80% consumption**, team members get a notification warning
4. At **100%**, an overage alert fires — additional hours are billed at the standard rate

#### 6.6 Close a Retainer Period

At month-end:

1. Go to the retainer detail page
2. The current period shows as **"Ready to Close"** (after the period end date)
3. Click **"Close Period"**
4. The system:
   - Calculates final consumption
   - Applies rollover policy (carries unused hours if configured)
   - Generates a **DRAFT invoice** for the retainer fee (+ any overage)
5. Review the draft invoice and proceed through the approval → send → paid workflow

---

### Chapter 7: Compliance & Administration

**The story**: As the firm grows, Sarah needs to stay on top of compliance and data governance.

#### 7.1 Compliance Dashboard

1. Navigate to **Compliance** in the sidebar
2. Review:
   - **Lifecycle distribution**: How many customers are in each status (Prospect, Onboarding, Active, Offboarded)
   - **Onboarding pipeline**: Customers currently being onboarded and their checklist progress
   - **Data requests**: Any subject access requests (POPIA/GDPR)
   - **Dormancy check**: Customers with no activity for extended periods

#### 7.2 Run a Dormancy Check

1. On the compliance dashboard, click **"Run Dormancy Check"**
2. The system identifies customers with no project activity beyond the configured threshold
3. Review the list and decide: re-engage or offboard

#### 7.3 Handle a Data Request

Imagine a former client requests their data (under POPIA):

1. Go to **Compliance → Data Requests**
2. Click **"New Request"**
3. Fill in:
   - Customer: select the customer
   - Type: **Subject Access Request** (SAR)
   - Requester email and notes
4. Process the request:
   - **Start Processing** → status changes to In Progress
   - **Export Data** → generates a data package (JSON/PDF) stored in S3
   - **Complete** → marks as fulfilled
5. For erasure requests, there's a separate **"Execute Deletion"** action (irreversible anonymization)

#### 7.4 Manage Retention Policies

1. Go to **Settings → Compliance**
2. Configure retention policies:
   - How long to keep completed project data
   - Dormancy thresholds
   - Auto-archive rules

#### 7.5 Review Audit Trail

1. The audit trail captures every significant action in the system
2. You can view entity-specific audit history on detail pages (via the Activity tab)
3. For org-wide audit queries, admin endpoints are available (future UI)

---

### Chapter 8: Document Generation

**The story**: GreenTech needs a formal engagement letter. Sarah uses document templates to generate it.

#### 8.1 Browse Document Templates

1. Go to **Settings → Templates**
2. You should see seeded templates (from the "Common Pack"):
   - Engagement Letter
   - Statement of Work
   - Invoice (used internally by the invoice preview)
3. Click into one to see the Thymeleaf HTML template

#### 8.2 Preview a Template

1. Click **"Preview"** on a template
2. Select the entity to render against (e.g., GreenTech project)
3. See the rendered HTML with real data filled in (customer name, project details, org branding)

#### 8.3 Generate a Document

1. Go to the **GreenTech** customer detail page
2. Click the **"Generate Document"** dropdown
3. Select a template (e.g., "Engagement Letter")
4. Preview the rendered output
5. Click **"Generate PDF"** — it's saved as a generated document linked to the customer
6. Find it in the **Generated Docs** tab on the customer/project page
7. Click **"Download"** to get the PDF

#### 8.4 Customize a Template

1. Go to **Settings → Templates**
2. Click **"Clone"** on an existing template
3. Edit the Thymeleaf HTML to match your firm's style
4. Use the preview to iterate until it looks right

> **Domain context**: Document templates are a huge time-saver for firms that send the same types of letters/reports regularly. The Thymeleaf engine pulls data directly from the platform — no copy-paste from spreadsheets.

---

### Chapter 9: The Dashboard & Health Monitoring

**The story**: Sarah starts each morning by checking her dashboards.

#### 9.1 Org Dashboard

1. Navigate to **Dashboard** in the sidebar
2. Review the KPI cards:
   - Total revenue (invoiced)
   - Hours logged
   - Active projects count
   - Overdue tasks
3. **Project Health widget**: Shows green/yellow/red status for each project
4. **Team Workload widget**: Hours per team member
5. **Recent Activity**: Cross-project activity feed
6. Adjust the date range filter to compare periods

#### 9.2 Personal Dashboard ("My Work")

1. Navigate to **My Work**
2. This is what each team member sees as their home:
   - Personal utilization rate
   - Upcoming task deadlines
   - Overdue tasks
   - Today's logged time
   - Available (unassigned) tasks to claim

#### 9.3 Project Health Detail

1. Go to any project → **Overview** tab
2. The **health score** considers:
   - Budget consumption vs. completion
   - Overdue tasks
   - Days since last activity
   - Team capacity
3. Click through the health reasons to understand what's driving the score

---

### Chapter 10: Advanced Features

#### 10.1 Saved Views

1. Go to **Projects** list
2. Apply some filters (e.g., tag = "Monthly")
3. Click **"Save View"** → name it "Monthly Engagements"
4. The saved view appears in the view dropdown for quick access
5. Works the same on Customers and Tasks lists

#### 10.2 Customer Portal (Dev Harness)

Test what your clients see:

1. Navigate to **http://localhost:8080/portal/dev/generate-link**
2. Select **Apex Accounting** org
3. Enter a customer email (e.g., `lisa@greentech.example.com`)
4. Click **"Generate Magic Link"**
5. Click the generated link — you're now in the **customer portal view**
6. Browse:
   - Project list (only GreenTech's projects)
   - Project detail (tasks, shared documents, shared comments)
   - The customer sees only **Shared** visibility items — Internal items are hidden

> **Important**: This is the Thymeleaf dev harness (backend-rendered). Phase 18 will build the real Next.js portal frontend. But the data and APIs are identical.

#### 10.3 Tags & Custom Fields on Entities

1. Go to any project/task/customer
2. Add tags and fill in custom field values
3. Go back to the list page and use tag/custom field filters
4. Save a filtered view for quick access

#### 10.4 Notification Preferences

1. Go to **Settings → Notifications**
2. Toggle preferences per notification type:
   - Task assigned to you
   - Comment on your task
   - Budget alert
   - Retainer consumption warning
3. Each type has in-app and email toggles

---

## Part 2: Quick-Reference Scenario Cards

Each card is self-contained. Prerequisites are listed at the top.

---

### QR-01: Create & Invoice a Customer

**Prereqs**: Org exists, at least one member, billing rates configured

1. **Customers** → New Customer → fill details
2. Transition lifecycle: Prospect → Onboarding → Active
3. **Projects** → New Project → link to customer → add members → add tasks
4. Log time entries against tasks (several hours across multiple tasks)
5. **Invoices** → New Invoice → select customer + project + date range → Generate
6. Review draft → Approve → Send → Record Payment

**Validates**: Customer lifecycle, project setup, time logging, invoice generation, lifecycle states

---

### QR-02: Set Up Rate Cards & Check Profitability

**Prereqs**: Org with members, at least one project with time entries

1. **Settings → Rates** → add billing rates per member
2. **Settings → Rates** → add cost rates per member
3. Go to a project → **Rates** tab → optionally add project-level overrides
4. Log some time entries (they snapshot the applicable rate)
5. **Profitability** page → check utilization, project margins, customer LTV
6. Project → **Financials** tab → project-level P&L

**Validates**: 3-level rate hierarchy, rate snapshotting, profitability calculations

---

### QR-03: Project Templates & Recurring Schedules

**Prereqs**: At least one project with tasks, one customer

1. Project detail → **"Save as Template"** → name it
2. **Settings → Project Templates** → review/edit the template
3. **Recurring Schedules** → New Schedule → pick template + customer + frequency
4. Set start date to today → the scheduler creates the project (runs daily)
5. Verify the new project was created with tasks from the template

**Validates**: Template creation, name tokens, schedule execution, task inheritance

---

### QR-04: Retainer Agreement Full Cycle

**Prereqs**: Customer exists, billing rates configured, at least one project linked to customer

1. **Retainers** → New Retainer → customer + hours + monthly fee + rollover policy
2. Log time on the customer's project → watch consumption percentage rise
3. At 80%: check for consumption warning notification
4. Close the period → verify draft invoice is generated
5. Review rollover: check next period's starting balance

**Validates**: Retainer creation, consumption tracking, notifications, period close, invoice generation, rollover

---

### QR-05: Compliance Workflow

**Prereqs**: At least one customer

1. Create customer → leave as Prospect
2. Transition to Onboarding → attach checklist
3. Complete checklist items → transition to Active
4. Later: **Compliance** → Run Dormancy Check
5. **Compliance → Data Requests** → create SAR → process → export → complete

**Validates**: Lifecycle transitions, checklist engine, dormancy detection, data request workflow

---

### QR-06: Document Template & PDF Generation

**Prereqs**: Org with branding configured, at least one customer with a project

1. **Settings → Templates** → review seeded templates
2. Clone a template → edit the HTML → preview
3. Go to customer/project → **Generate Document** → select template → preview → generate PDF
4. Check **Generated Docs** tab → download the PDF
5. Verify branding (logo, colors, footer) appears in the PDF

**Validates**: Template CRUD, Thymeleaf rendering, PDF generation, S3 storage, branding

---

### QR-07: Custom Fields, Tags & Saved Views

**Prereqs**: Org exists

1. **Settings → Custom Fields** → create fields for Projects/Customers/Tasks
2. Create a Field Group → add fields to it
3. **Settings → Tags** → create several tags
4. Go to entities → apply tags and fill in custom field values
5. Go to list pages → filter by tag / custom field value
6. **Save View** → verify it persists and filters correctly

**Validates**: Field definitions, groups, tag CRUD, entity tagging, view persistence, filter queries

---

### QR-08: Team Collaboration

**Prereqs**: Multiple members in org, project with tasks

1. Create unassigned tasks on a project
2. As a member: **My Work** → browse available tasks → **Claim** a task
3. Log time → add comments on the task
4. Another member comments → first member gets a notification
5. Check the project **Activity** tab — see the full timeline
6. **Release** the task (un-claim) → it goes back to the pool

**Validates**: Task claim/release, comments, notifications, activity feed, "My Work" page

---

### QR-09: Budget Tracking & Alerts

**Prereqs**: Project with linked customer and time entries

1. Project → **Budget** tab → Configure: 20 hours, R15,000, alert at 80%
2. Log time until you pass the 80% threshold
3. Check for budget alert notification
4. Project → **Overview** → see budget status indicator
5. Dashboard → check project health (budget impacts health score)
6. Delete the budget to reset

**Validates**: Budget configuration, consumption tracking, alert notifications, health scoring

---

### QR-10: Customer Portal Experience

**Prereqs**: Customer with project, documents (some Shared visibility), comments

1. **http://localhost:8080/portal/dev/generate-link**
2. Select org → enter customer email → generate link
3. Click link → land on portal dashboard
4. Browse: projects, documents, comments
5. Verify: only **Shared** items are visible, **Internal** items are hidden
6. Verify: no edit/delete capabilities (read-only)

**Validates**: Magic link auth, portal JWT, read-model projection, visibility filtering

---

## Appendix: Entity Relationship Mental Model

```
Organization (tenant)
├── Members (synced from Clerk)
├── OrgSettings (currency, branding)
├── BillingRates (org-level defaults)
├── CostRates (per member)
├── Tags
├── FieldDefinitions & FieldGroups
├── DocumentTemplates
├── ChecklistTemplates
├── ProjectTemplates
├── RetentionPolicies
│
├── Customers
│   ├── Lifecycle (Prospect → Onboarding → Active → Offboarded)
│   ├── ChecklistInstances (from templates)
│   ├── Customer-scoped Documents
│   ├── Customer-specific BillingRates (overrides)
│   ├── RetainerAgreements → RetainerPeriods
│   ├── Tags, Custom Field Values
│   └── DataRequests
│
├── Projects
│   ├── ProjectMembers (with lead role)
│   ├── Tasks
│   │   ├── TimeEntries (with rate snapshots)
│   │   ├── Comments
│   │   └── Tags, Custom Field Values
│   ├── Project-scoped Documents
│   ├── ProjectBudget
│   ├── Project-specific BillingRates (overrides)
│   ├── Tags, Custom Field Values
│   └── GeneratedDocuments
│
├── Invoices → InvoiceLines (linked to time entries)
├── Notifications & NotificationPreferences
├── AuditEvents
├── SavedViews
├── RecurringSchedules → ScheduleExecutions
│
└── Portal (read-model)
    ├── PortalContacts
    ├── PortalProjects, PortalDocuments
    ├── PortalComments, PortalProjectSummaries
    └── MagicLinks
```

---

## Appendix: Keyboard Shortcuts & Tips

- **Sidebar collapse**: Click the collapse button at the bottom of the sidebar
- **Date range filters**: Most dashboard/report pages support `?from=YYYY-MM-DD&to=YYYY-MM-DD` URL params
- **Quick navigation**: Use the org switcher to jump between organizations
- **Notification badge**: Real-time unread count in the header bell icon

---

## Appendix: What Each Role Can Do

| Action | Member | Admin | Owner |
|--------|--------|-------|-------|
| View projects/tasks | Yes | Yes | Yes |
| Create projects | No | Yes | Yes |
| Delete projects | No | No | Yes |
| Create customers | No | Yes | Yes |
| Manage billing rates | No | Yes | Yes |
| View/create invoices | No | Yes | Yes |
| View profitability | No | Yes | Yes |
| Configure settings | No | Yes | Yes |
| Manage retainers | No | Yes | Yes |
| Compliance features | No | Yes | Yes |
| Log time & claim tasks | Yes | Yes | Yes |
| Upload documents | Yes | Yes | Yes |
| Add comments | Yes | Yes | Yes |

> **Note**: Project leads (any role) get elevated access *within their project* — edit project, manage budget, view financials.
