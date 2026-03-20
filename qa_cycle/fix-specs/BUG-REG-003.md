# Fix Spec: BUG-REG-003 — Customer list has no free-text search input

## Problem
The customer list page (`/customers`) has lifecycle status filtering (All, Prospect, Onboarding, Active, etc.) and completeness sorting/filtering, but no free-text search capability. Users cannot search customers by name, email, or phone number. This is a missing feature identified in regression test CUST-01 #4.

## Root Cause (confirmed)
Neither the backend nor frontend implements customer search:

- **Backend**: `CustomerController.listCustomers()` at `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` (line 92) accepts `view` (UUID), `lifecycleStatus` (enum), and `allParams` (for saved view filters), but no `search` or `q` parameter.
- **Backend repository**: `CustomerRepository` has no `findByNameContaining`, search method, or native query for text search.
- **Frontend**: `frontend/app/(app)/org/[slug]/customers/page.tsx` has lifecycle filter links (line 219) and completeness toggle (line 241) but no `<Input>` element for free-text search. The `searchParams` handling (line 77) reads `view`, `lifecycleStatus`, `showIncomplete`, `sortBy`, `sortDir` but not `search` or `q`.

## Fix

### Step 1: Backend — Add search parameter to CustomerController

In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java`:

Add a `@RequestParam(required = false) String search` parameter to `listCustomers()`. When non-null, filter customers whose `name`, `email`, or `phone` contains the search term (case-insensitive).

Implementation options:
- **Option A (Simple)**: Add a method to `CustomerRepository`:
  ```java
  @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%'))")
  List<Customer> searchByNameOrEmailOrPhone(@Param("search") String search);
  ```
- **Option B (Service layer)**: Add to `CustomerService` a `searchCustomers(String query)` method that delegates to the repository.

Compose with existing `lifecycleStatus` filter: if both `search` and `lifecycleStatus` are provided, apply both.

### Step 2: Frontend — Add search input to customers page

In `frontend/app/(app)/org/[slug]/customers/page.tsx`:

1. Read `search` from `resolvedSearchParams` (line 94 area).
2. Pass `search` to the API endpoint: `/api/customers?search=<term>` (or combine with existing `lifecycleStatus`).
3. Add a client-side search input component above the table. Since the page is a server component, create a small client component `CustomerSearchInput` that navigates via URL params:

```tsx
// frontend/components/customers/customer-search-input.tsx
"use client";

import { useRouter, useSearchParams, usePathname } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";
import { useDebouncedCallback } from "use-debounce"; // or manual debounce

export function CustomerSearchInput() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const handleSearch = useDebouncedCallback((term: string) => {
    const params = new URLSearchParams(searchParams.toString());
    if (term) {
      params.set("search", term);
    } else {
      params.delete("search");
    }
    router.push(`${pathname}?${params.toString()}`);
  }, 300);

  return (
    <div className="relative">
      <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      <Input
        placeholder="Search customers..."
        defaultValue={searchParams.get("search") ?? ""}
        onChange={(e) => handleSearch(e.target.value)}
        className="pl-9"
      />
    </div>
  );
}
```

4. Place the search input above the lifecycle filter links.

## Scope
Both — Backend (repository + controller) and Frontend (new component + page integration).

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java`
- `frontend/app/(app)/org/[slug]/customers/page.tsx`

Files to create:
- `frontend/components/customers/customer-search-input.tsx`

Migration needed: no

## Verification
Re-run CUST-01 #4 (Search customer list). Type a customer name in the search input and verify the list filters to matching customers.

## Estimated Effort
M (30 min - 2 hr) — Backend requires a new repository method + controller parameter. Frontend requires a new client component with debounced input and server component integration.

## WONT_FIX Consideration
This is a missing feature, not a bug. If the QA cycle scope is strictly "regression of existing features," this should be marked WONT_FIX since customer search was never implemented. However, the regression test suite (CUST-01 #4) explicitly specifies it as expected behavior, so it's a legitimate gap.

**Recommendation**: Mark as WONT_FIX for this bugfix cycle. Customer search is a feature enhancement, not a regression. Log it as a backlog item for a future sprint.
