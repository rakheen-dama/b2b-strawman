# ADR-187: Bulk Time Entry UX Pattern

**Status**: Accepted
**Date**: 2026-03-16
**Phase**: 48 (QA Gap Closure)

## Context

The QA cycle with Thornton & Associates revealed that time entry creation is the highest-friction daily operation in DocTeams. Each entry requires 6-8 clicks: navigate to project, open task, click "Log Time," fill in date/hours/description, toggle billable, and save. For a 3-person firm logging 5-8 entries per person per day, this means 90-192 clicks daily just for time tracking.

Professional services firms typically have predictable weekly patterns: the same team members work on the same engagements week to week, logging similar hours. The most effective time tracking tools (Harvest, Toggl, Clockify) exploit this regularity with weekly grid views and copy-forward features.

The backend `TimeEntryService` handles single-entry creation with rate snapshot logic, billable flag handling, and budget threshold checks. A bulk entry feature needs both a backend batch endpoint and a frontend interface that reduces per-entry friction from 6-8 clicks to 1-2 (type hours in a cell, tab to next).

## Options Considered

### Option 1: Weekly grid (Harvest/Toggl style)

A timesheet grid where rows are tasks and columns are days of the week (Mon-Sun). Each cell is an editable hours input. The grid pre-populates with tasks the user has recently worked on or is currently assigned to. A "Save" button batch-submits all new/changed entries. A "Copy Previous Week" button loads the prior week's task/hours pattern into the current week.

- **Pros:**
  - Highest daily-use value: the grid is a single view for an entire week's time tracking
  - Minimal clicks per entry: type a number in a cell and Tab to the next -- 1-2 actions per entry vs. 6-8
  - Copy Previous Week eliminates entry creation for weeks with repeating patterns (common in accounting firms)
  - Row and column totals provide immediate feedback on weekly utilization
  - Familiar pattern: Harvest, Toggl, Clockify, and most enterprise time tracking tools use this layout
  - Week navigation (forward/back) enables quick catch-up for missed days
  - Batch save reduces network round-trips (1 request vs. N individual creates)

- **Cons:**
  - Moderate frontend complexity: grid state management, cell editing, dirty tracking, batch submission
  - Does not handle multi-project time splitting within a single day intuitively (one cell per task/day)
  - Task row selection requires a mechanism to add new tasks to the grid (autocomplete or dropdown)
  - Description field is awkward in a grid layout (usually handled via a popover or secondary click)
  - Not suitable for historical bulk imports (e.g., migrating from another system)

### Option 2: Spreadsheet paste

A free-form paste area where users copy-paste from Excel/Google Sheets. The system parses the pasted text (tab-delimited), maps columns to fields (date, task, hours, description), shows a preview, and batch-submits.

- **Pros:**
  - Leverages existing tools (Excel, Google Sheets) that accountants already use daily
  - Flexible column mapping handles various source formats
  - Good for large one-time imports (e.g., catching up on a month of unlogged time)
  - Low frontend complexity: textarea + parser + preview table

- **Cons:**
  - High error rate: paste data is unstructured, column misalignment is common
  - Task matching is fragile: pasted task names must exactly match existing task names (or require fuzzy matching)
  - Not suitable for daily use: the paste workflow is batch-oriented, not interactive
  - No visual feedback during entry (no running totals, no week view)
  - No copy-forward capability -- each week requires a fresh paste
  - Column format must be documented and users must adhere to it

### Option 3: CSV-only import

A file upload interface: download a CSV template, fill it in, upload it, preview parsed rows with validation status, and import. Uses the same batch backend endpoint.

- **Pros:**
  - Structured format: CSV columns are predefined, reducing parsing errors vs. free-form paste
  - Template CSV provides a guide for required fields and formats
  - Good for bulk historical imports and system migrations
  - Simple frontend: file upload + preview table + import button
  - Can handle large volumes (hundreds of entries in one upload)

- **Cons:**
  - High friction for daily use: export template, fill in, save file, upload file, review, import -- more clicks than individual entry creation
  - File-based workflow is out of step with modern web UX expectations
  - Task and project matching by name is fragile (same as paste option)
  - No interactive feedback: errors are discovered only after upload and parse
  - No copy-forward capability
  - Accountants who work in spreadsheets may prefer paste over file upload

## Decision

**Option 1 -- Weekly grid as the primary UX pattern.** CSV import (Option 3) is retained as a secondary stretch goal for historical bulk imports.

## Rationale

The weekly grid maximizes daily-use value, which is the primary goal. Time tracking is the most frequent operation in a professional services firm (5-8 entries per person per day). The grid reduces per-entry friction from 6-8 clicks to 1-2 (type and Tab), and Copy Previous Week eliminates entry creation entirely for repeating weeks. This directly addresses the QA finding that time entry is the highest-friction daily workflow.

The grid is the industry standard for a reason: Harvest, Toggl, Clockify, Float, and virtually every professional time tracking tool converges on the weekly grid layout. Accountants at Thornton & Associates already understand this pattern from prior tools. There is no learning curve.

Spreadsheet paste (Option 2) is rejected because daily-use friction is higher than the weekly grid: users must maintain a separate spreadsheet, format it correctly, and paste it. The error rate from column misalignment and unstructured text makes it unsuitable as a primary interface. For occasional bulk entry, CSV import (Option 3) provides the same benefit with a more structured and reliable approach.

CSV import is retained as a stretch goal (not a primary deliverable) because it addresses a different use case: historical bulk imports and system migrations. This is a one-time or infrequent operation, not a daily workflow. It shares the same backend batch endpoint as the weekly grid, so the marginal implementation cost is the file upload UI and CSV parser.

The backend `POST /api/time-entries/batch` endpoint serves both the weekly grid and the CSV import. It accepts an array of entries, validates each independently, applies rate snapshots, and returns partial success results. This design decouples the batch processing logic from the frontend interface, allowing both UX patterns to share the same endpoint.

## Consequences

- The weekly grid is the primary time tracking interface for daily use. The existing per-task time entry form remains available for one-off entries.
- Copy Previous Week pre-fills the grid but does not auto-save. Users review and adjust before saving. This prevents accidental duplicate entries.
- Descriptions are handled via a cell popover or secondary click (not inline in the grid). Each cell shows hours only; description is optional per entry.
- The `POST /api/time-entries/batch` endpoint supports partial success: valid entries are created, invalid entries return per-entry errors. The frontend displays both success counts and error details.
- CSV import is a stretch goal and may be deferred to a follow-up phase if the weekly grid implementation takes longer than expected.
- The batch endpoint has a 50-entry limit per request. A typical weekly grid submission for one user is 7-35 entries (1-5 tasks x 7 days), well within the limit.
- Rate snapshots are calculated per entry at batch creation time, consistent with single-entry creation. Budget threshold checks fire per entry as well, potentially generating multiple `BudgetThresholdEvent` notifications in a single batch.
