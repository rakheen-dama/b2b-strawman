# Customer Portal

## Overview

A separate frontend application that gives customers self-service access to their projects, documents, and invoices. Runs on a different port/domain from the main app.

### Key Characteristics
- **No password**: Authentication via magic link emails only
- **Branded**: Shows the firm's logo, brand color, and footer text
- **Read-heavy**: Customers view project status, download documents, pay invoices. Limited write access (comments only).
- **Separate app**: Independent Next.js application, separate deployment

---

## Authentication

### Magic Link Flow
1. Customer navigates to portal login page
2. Enters email address
3. Backend sends magic link email (or shows link in dev mode)
4. Customer clicks link → token exchanged for JWT
5. JWT stored in localStorage (not HttpOnly cookies — acceptable for limited scope)
6. Session persists until token expires or customer logs out

### Session Management
- JWT checked on each page load
- Expired JWT → redirect to login
- 401 from API → clear session, redirect to login
- No session refresh (short-lived tokens, re-auth via new magic link)

---

## Portal Pages

### Login Page (`/portal/login`)
- Email input field
- Org branding (logo, name) loaded from backend
- "Send Magic Link" button
- Success state: "Check your email" message

### Projects Page (`/portal/projects`) — Dashboard
- Grid of project cards
- Each card: project name, document count, created date
- Click → project detail
- **Pending Acceptances Banner**: if customer has documents awaiting acceptance, shown prominently at top

### Project Detail (`/portal/projects/[id]`)
- Project header: name, status badge
- Summary card: key project metrics
- **Tasks**: read-only task list with status and assignee
- **Documents**: documents marked as SHARED, with download links
- **Comments**: comment thread (customer can add SHARED comments)

### Invoices Page (`/portal/invoices`)
- Table of all invoices for this customer
- Columns: invoice number, issue date, due date, status, total
- Status badges (Draft, Sent, Paid, Overdue)
- Download PDF link per invoice
- "Pay Now" link (if payment link available)

### Invoice Detail (`/portal/invoices/[id]`)
- Full invoice display matching the firm-side view
- Line items table (description, qty, unit price, tax, amount)
- Tax breakdown section
- Total and outstanding amount
- **Payment section**: "Pay Now" button → redirects to payment provider
- Payment status polling (checks every 3 seconds after redirect, 30s timeout)

### Profile Page (`/portal/profile`)
- Contact name, email, role
- Customer name (which firm client they represent)
- Read-only (contact details managed by firm)

### Document Acceptance (`/portal/accept/[token]`) — Unauthenticated
- PDF viewer showing the document
- Acceptance status display
- If PENDING: name confirmation field + "Accept" button
- If ACCEPTED: shows accepted date and acceptor name
- If EXPIRED/REVOKED: shows status message

---

## Portal Navigation
- **Header**: Org logo, "Projects" link, "Invoices" link, customer name, logout button
- **Footer**: "Powered by DocTeams" + org footer text (from branding settings)
- **Mobile**: Hamburger menu for navigation

---

## Portal Branding
Configurable by the firm in Settings → Organization:

| Setting | Where Used |
|---------|-----------|
| Logo | Portal header, login page |
| Brand Color | Active nav links, CTA buttons, accent elements |
| Footer Text | Portal footer |
| Organization Name | Login page, portal header |

---

## Portal API Surface
The portal has its own API endpoints (separate from the main app API):

### Unauthenticated
| Endpoint | Purpose |
|----------|---------|
| `POST /portal/auth/request-link` | Request magic link email |
| `POST /portal/auth/exchange` | Exchange token for JWT |
| `GET /portal/branding` | Fetch org branding |
| `GET /portal/acceptances/[token]` | Get acceptance request data |
| `GET /portal/acceptances/[token]/pdf` | Download acceptance PDF |
| `POST /portal/acceptances/[token]` | Submit acceptance |

### Authenticated (Bearer JWT)
| Endpoint | Purpose |
|----------|---------|
| `GET /portal/projects` | List customer's projects |
| `GET /portal/projects/[id]` | Project detail |
| `GET /portal/projects/[id]/tasks` | Project tasks |
| `GET /portal/projects/[id]/documents` | Project documents |
| `GET /portal/projects/[id]/comments` | Project comments |
| `GET /portal/projects/[id]/summary` | Project summary metrics |
| `POST /portal/projects/[id]/comments` | Add comment |
| `GET /portal/invoices` | List customer's invoices |
| `GET /portal/invoices/[id]` | Invoice detail |
| `GET /portal/invoices/[id]/download` | Download invoice PDF |
| `GET /portal/invoices/[id]/payment-status` | Check payment status |
| `GET /portal/me` | Customer contact profile |

---

## Design Considerations for UX Expert
- Portal is a **consumer-grade experience** — simple, clean, no learning curve
- Customers may visit rarely (monthly invoice check, quarterly project review)
- Must work on mobile (clients check invoices on phones)
- Branding must feel like it's the firm's tool, not a third-party app
- Acceptance flow is often the first touchpoint — must be trustworthy and professional
