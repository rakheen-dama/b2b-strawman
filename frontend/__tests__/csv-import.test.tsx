import { describe, it, expect } from "vitest";
import { parseCsv } from "@/components/time-tracking/csv-import-dialog";
import type { GridTaskRow } from "@/components/time-tracking/weekly-time-grid";

const availableTasks: GridTaskRow[] = [
  {
    id: "t1",
    projectId: "p1",
    projectName: "Project Alpha",
    title: "Design wireframes",
  },
  {
    id: "t2",
    projectId: "p2",
    projectName: "Project Beta",
    title: "Write API docs",
  },
];

describe("CSV Import Parser", () => {
  it("valid CSV file parsed and displayed correctly", () => {
    const csv = `date,task_name,project_name,hours,description,billable
2026-03-16,Design wireframes,Project Alpha,2,Worked on mockups,true
2026-03-17,Write API docs,Project Beta,3,,true`;

    const rows = parseCsv(csv, availableTasks);

    expect(rows).toHaveLength(2);

    // First row
    expect(rows[0].valid).toBe(true);
    expect(rows[0].errors).toHaveLength(0);
    expect(rows[0].date).toBe("2026-03-16");
    expect(rows[0].taskName).toBe("Design wireframes");
    expect(rows[0].projectName).toBe("Project Alpha");
    expect(rows[0].hours).toBe(2);
    expect(rows[0].description).toBe("Worked on mockups");
    expect(rows[0].billable).toBe(true);
    expect(rows[0].matchedTaskId).toBe("t1");

    // Second row
    expect(rows[1].valid).toBe(true);
    expect(rows[1].errors).toHaveLength(0);
    expect(rows[1].date).toBe("2026-03-17");
    expect(rows[1].taskName).toBe("Write API docs");
    expect(rows[1].projectName).toBe("Project Beta");
    expect(rows[1].hours).toBe(3);
    expect(rows[1].matchedTaskId).toBe("t2");
  });

  it("invalid rows show error indicators with messages", () => {
    const csv = `date,task_name,project_name,hours,description,billable
invalid-date,Design wireframes,Project Alpha,2,,true
2026-03-16,,Project Alpha,2,,true
2026-03-16,Design wireframes,,2,,true
2026-03-16,Design wireframes,Project Alpha,-1,,true
2026-03-16,Nonexistent task,Project Alpha,2,,true
2026-03-16,Design wireframes,Project Alpha,0,,true`;

    const rows = parseCsv(csv, availableTasks);

    expect(rows).toHaveLength(6);

    // Row 0: invalid date
    expect(rows[0].valid).toBe(false);
    expect(rows[0].errors).toContain("Date must be YYYY-MM-DD format");

    // Row 1: missing task name
    expect(rows[1].valid).toBe(false);
    expect(rows[1].errors).toContain("Task name is required");

    // Row 2: missing project name
    expect(rows[2].valid).toBe(false);
    expect(rows[2].errors).toContain("Project name is required");

    // Row 3: negative hours
    expect(rows[3].valid).toBe(false);
    expect(rows[3].errors).toContain("Hours must be a positive number");

    // Row 4: unmatched task
    expect(rows[4].valid).toBe(false);
    expect(rows[4].errors).toContain("No matching task found");

    // Row 5: zero hours
    expect(rows[5].valid).toBe(false);
    expect(rows[5].errors).toContain("Hours must be a positive number");
  });

  it("handles empty CSV content", () => {
    const rows = parseCsv("", availableTasks);
    expect(rows).toHaveLength(0);
  });

  it("handles CSV with only header row", () => {
    const csv = "date,task_name,project_name,hours,description,billable";
    const rows = parseCsv(csv, availableTasks);
    expect(rows).toHaveLength(0);
  });

  it("task matching is case-insensitive", () => {
    const csv = `date,task_name,project_name,hours,description,billable
2026-03-16,DESIGN WIREFRAMES,PROJECT ALPHA,2,,true`;

    const rows = parseCsv(csv, availableTasks);

    expect(rows).toHaveLength(1);
    expect(rows[0].valid).toBe(true);
    expect(rows[0].matchedTaskId).toBe("t1");
  });

  it("parses billable field correctly", () => {
    const csv = `date,task_name,project_name,hours,description,billable
2026-03-16,Design wireframes,Project Alpha,2,,false
2026-03-17,Write API docs,Project Beta,2,,true`;

    const rows = parseCsv(csv, availableTasks);

    expect(rows[0].billable).toBe(false);
    expect(rows[1].billable).toBe(true);
  });

  it("validates hours exceed 24", () => {
    const csv = `date,task_name,project_name,hours,description,billable
2026-03-16,Design wireframes,Project Alpha,25,,true`;

    const rows = parseCsv(csv, availableTasks);

    expect(rows[0].valid).toBe(false);
    expect(rows[0].errors).toContain("Hours cannot exceed 24");
  });
});
