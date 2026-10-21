import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 613B — getDiary reads the body AND the X-Diary-Types header (raw fetch), plus the
// pure helpers in diary-types.ts that page.tsx and the client views rely on.

vi.mock("server-only", () => ({}));

const { mockApiGet, mockListMatters, mockGetAuthFetchOptions, mockRedirectToReLogin } = vi.hoisted(
  () => ({
    mockApiGet: vi.fn(),
    mockListMatters: vi.fn(),
    mockGetAuthFetchOptions: vi.fn(),
    mockRedirectToReLogin: vi.fn(() => {
      throw new Error("NEXT_REDIRECT");
    }),
  })
);

vi.mock("@/lib/api/client", () => {
  class ApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: unknown
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    API_BASE: "http://backend.test",
    ApiError,
    api: { get: mockApiGet },
    getAuthFetchOptions: mockGetAuthFetchOptions,
    resolveServerReturnTo: vi.fn().mockResolvedValue("/dashboard"),
  };
});
vi.mock("@/lib/api/matters", () => ({ listMatters: mockListMatters }));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: mockRedirectToReLogin }));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import {
  getDiary,
  getDiaryMatters,
  getDiaryMembers,
} from "@/app/(app)/org/[slug]/diary/diary-actions";
import {
  addDays,
  addMonths,
  buildDiaryQuery,
  daysBetween,
  diaryWindow,
  filterDiaryEvents,
  firstOfMonth,
  formatDiaryRangeLabel,
  formatDiaryTime,
  lastOfMonth,
  monthGridWeeks,
  sameWindow,
  stepDiaryWindow,
  windowContains,
  groupByDate,
  humaniseSubtype,
  isValidIsoDate,
  mondayOf,
  parseDiaryKinds,
  parseDiaryKindsParam,
  parseDiaryView,
  parseTypesHeader,
  partitionOverdueBand,
  shouldUseServerFeeEarnerFilter,
  type DiaryEventResponse,
} from "@/app/(app)/org/[slug]/diary/diary-types";

function okResponse(body: unknown, typesHeader: string | null) {
  const headers = new Headers({ "content-type": "application/json" });
  if (typesHeader !== null) headers.set("X-Diary-Types", typesHeader);
  return {
    ok: true,
    status: 200,
    statusText: "OK",
    headers,
    json: () => Promise.resolve(body),
  };
}

function event(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "e-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "File plea",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Dlamini v Road Accident Fund",
    overdue: false,
    href: "/matters/m-1?taskId=e-1",
    ...overrides,
  };
}

describe("getDiary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAuthFetchOptions.mockResolvedValue({ headers: { Authorization: "Bearer t" } });
    mockFetch.mockResolvedValue(okResponse([], "TASK_DUE,MATTER_DUE,COURT_DATE,PRESCRIPTION"));
  });

  it("calls /api/diary with only from and to by default", async () => {
    await getDiary({ from: "2026-10-19", to: "2026-10-25" });
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe("http://backend.test/api/diary?from=2026-10-19&to=2026-10-25");
    expect(init).toMatchObject({
      method: "GET",
      redirect: "manual",
      headers: { Authorization: "Bearer t", "Content-Type": "application/json" },
    });
  });

  it("adds types, feeEarnerId, matterId and includeOverdue=false only when given", async () => {
    await getDiary({
      from: "2026-10-19",
      to: "2026-10-25",
      types: ["COURT_DATE", "PRESCRIPTION"],
      feeEarnerId: "fe-1",
      matterId: "m-1",
      includeOverdue: false,
    });
    expect(mockFetch.mock.calls[0][0]).toBe(
      "http://backend.test/api/diary?from=2026-10-19&to=2026-10-25&types=COURT_DATE%2CPRESCRIPTION&feeEarnerId=fe-1&matterId=m-1&includeOverdue=false"
    );
  });

  it("parses X-Diary-Types into served kinds in display order", async () => {
    mockFetch.mockResolvedValue(okResponse([], "TASK_DUE,MATTER_DUE"));
    const result = await getDiary({ from: "2026-10-19", to: "2026-10-25" });
    expect(result.typesServed).toEqual(["MATTER_DUE", "TASK_DUE"]);
  });

  it("treats an empty or missing X-Diary-Types header as no kinds served", async () => {
    mockFetch.mockResolvedValueOnce(okResponse([], ""));
    expect((await getDiary({ from: "2026-10-19", to: "2026-10-25" })).typesServed).toEqual([]);
    mockFetch.mockResolvedValueOnce(okResponse([], null));
    expect((await getDiary({ from: "2026-10-19", to: "2026-10-25" })).typesServed).toEqual([]);
  });

  it("passes events through with NON_NULL-omitted fields left absent", async () => {
    const body = [event()];
    mockFetch.mockResolvedValue(okResponse(body, "TASK_DUE"));
    const { events } = await getDiary({ from: "2026-10-19", to: "2026-10-25" });
    expect(events).toHaveLength(1);
    expect(events[0].time).toBeUndefined();
    expect(events[0].subtype).toBeUndefined();
    expect(events[0].feeEarnerId ?? null).toBeNull();
  });

  it("rejects with ApiError carrying the problem detail on a 400", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 400,
      statusText: "Bad Request",
      headers: new Headers({ "content-type": "application/problem+json" }),
      json: () =>
        Promise.resolve({
          type: "diary.window-too-large",
          title: "Diary window too large",
          detail: "The diary window must not exceed 92 days (requested 120).",
        }),
    });
    await expect(getDiary({ from: "2026-01-01", to: "2026-05-01" })).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "The diary window must not exceed 92 days (requested 120).",
      detail: { type: "diary.window-too-large" },
    });
  });

  it("keeps statusText when the error body is not JSON", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 502,
      statusText: "Bad Gateway",
      headers: new Headers(),
      json: () => Promise.reject(new SyntaxError("Unexpected token")),
    });
    await expect(getDiary({ from: "2026-10-19", to: "2026-10-25" })).rejects.toMatchObject({
      status: 502,
      message: "Bad Gateway",
    });
  });

  it("maps a gateway redirect to a 401 ApiError", async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 302,
      statusText: "Found",
      headers: new Headers({ Location: "/login" }),
      json: () => Promise.resolve({}),
    });
    await expect(getDiary({ from: "2026-10-19", to: "2026-10-25" })).rejects.toMatchObject({
      status: 401,
      message: "Authentication session expired",
    });
  });

  it("funnels to re-login when there is no usable session", async () => {
    mockGetAuthFetchOptions.mockRejectedValue(new Error("no session"));
    await expect(getDiary({ from: "2026-10-19", to: "2026-10-25" })).rejects.toThrow(
      "NEXT_REDIRECT"
    );
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/dashboard", "expired");
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

describe("getDiaryMembers / getDiaryMatters", () => {
  beforeEach(() => vi.clearAllMocks());

  it("maps /api/members to id, name and email", async () => {
    mockApiGet.mockResolvedValue([
      {
        id: "u-1",
        name: "Thandi Nkosi",
        email: "thandi@firm.test",
        avatarUrl: null,
        orgRole: "member",
      },
    ]);
    await expect(getDiaryMembers()).resolves.toEqual([
      { id: "u-1", name: "Thandi Nkosi", email: "thandi@firm.test" },
    ]);
    expect(mockApiGet).toHaveBeenCalledWith("/api/members");
  });

  it("maps listMatters() to options sorted by name", async () => {
    mockListMatters.mockResolvedValue([
      { id: "m-2", name: "Zulu Estate", referenceNumber: null, status: "ACTIVE" },
      {
        id: "m-1",
        name: "Dlamini v Road Accident Fund",
        referenceNumber: "LIT-001",
        status: "ACTIVE",
      },
    ]);
    await expect(getDiaryMatters()).resolves.toEqual([
      { id: "m-1", name: "Dlamini v Road Accident Fund", referenceNumber: "LIT-001" },
      { id: "m-2", name: "Zulu Estate", referenceNumber: null },
    ]);
    expect(mockListMatters).toHaveBeenCalledWith();
  });
});

describe("diary-types helpers", () => {
  it("mondayOf snaps any day to its Monday", () => {
    expect(mondayOf("2026-10-25")).toBe("2026-10-19"); // Sunday
    expect(mondayOf("2026-10-19")).toBe("2026-10-19"); // Monday
    expect(mondayOf("2026-10-21")).toBe("2026-10-19"); // Wednesday
    expect(mondayOf("2026-11-01")).toBe("2026-10-26"); // Sunday across a month end
  });

  it("addDays and daysBetween handle month and year ends", () => {
    expect(addDays("2026-10-26", 6)).toBe("2026-11-01");
    expect(addDays("2026-12-28", 6)).toBe("2027-01-03");
    expect(daysBetween("2026-10-19", "2026-10-25")).toBe(6);
    expect(daysBetween("2026-01-01", "2026-04-03")).toBe(92);
  });

  it("isValidIsoDate rejects malformed and impossible dates", () => {
    expect(isValidIsoDate("2026-10-21")).toBe(true);
    expect(isValidIsoDate("2026-02-31")).toBe(false);
    expect(isValidIsoDate("2026/10/21")).toBe(false);
    expect(isValidIsoDate("")).toBe(false);
    expect(isValidIsoDate(undefined)).toBe(false);
  });

  // 613C amendment (B-7): Day and Month are real views now; only junk falls back to week.
  it("parseDiaryView accepts day, week, month and list and falls back to week", () => {
    expect(parseDiaryView("day")).toBe("day");
    expect(parseDiaryView("week")).toBe("week");
    expect(parseDiaryView("month")).toBe("month");
    expect(parseDiaryView("list")).toBe("list");
    expect(parseDiaryView("bogus")).toBe("week");
    expect(parseDiaryView(undefined)).toBe("week");
  });

  it("parseDiaryKinds keeps known kinds in display order", () => {
    expect(parseDiaryKinds("PRESCRIPTION,BOGUS,COURT_DATE")).toEqual([
      "COURT_DATE",
      "PRESCRIPTION",
    ]);
    expect(parseDiaryKinds("")).toEqual([]);
    expect(parseDiaryKinds(undefined)).toEqual([]);
    expect(parseTypesHeader("TASK_DUE,,NOPE")).toEqual(["TASK_DUE"]);
  });

  it("parseDiaryKindsParam distinguishes an absent ?types= from an empty one", () => {
    expect(parseDiaryKindsParam(undefined)).toBeNull();
    expect(parseDiaryKindsParam(null)).toBeNull();
    expect(parseDiaryKindsParam("")).toEqual([]);
    expect(parseDiaryKindsParam("TASK_DUE,BOGUS")).toEqual(["TASK_DUE"]);
  });

  it("buildDiaryQuery omits an empty types list", () => {
    expect(buildDiaryQuery({ from: "2026-10-19", to: "2026-10-25", types: [] })).toBe(
      "from=2026-10-19&to=2026-10-25"
    );
    expect(buildDiaryQuery({ from: "2026-10-19", to: "2026-10-25", includeOverdue: true })).toBe(
      "from=2026-10-19&to=2026-10-25"
    );
  });

  it("shouldUseServerFeeEarnerFilter is true only for windows over 31 days", () => {
    expect(shouldUseServerFeeEarnerFilter("2026-10-19", "2026-10-25")).toBe(false);
    expect(shouldUseServerFeeEarnerFilter("2026-10-01", "2026-11-01")).toBe(false); // 31
    expect(shouldUseServerFeeEarnerFilter("2026-10-01", "2026-11-02")).toBe(true); // 32
  });

  it("filterDiaryEvents filters by kind, fee earner and matter, preserving order", () => {
    const events = [
      event({ id: "a", kind: "COURT_DATE", feeEarnerId: "fe-1" }),
      event({ id: "b", kind: "TASK_DUE", feeEarnerId: "fe-2", matterId: "m-2" }),
      event({ id: "c", kind: "TASK_DUE" }), // no fee earner
      event({ id: "d", kind: "PRESCRIPTION", feeEarnerId: "fe-1" }),
    ];
    const all = { kinds: ["COURT_DATE", "PRESCRIPTION", "TASK_DUE"] as const };
    const ids = (f: Parameters<typeof filterDiaryEvents>[1]) =>
      filterDiaryEvents(events, f).map((e) => e.id);

    expect(ids({ kinds: [...all.kinds], feeEarnerId: null, matterId: null })).toEqual([
      "a",
      "b",
      "c",
      "d",
    ]);
    expect(ids({ kinds: ["TASK_DUE"], feeEarnerId: null, matterId: null })).toEqual(["b", "c"]);
    // Events with no fee earner are excluded when filtering by one.
    expect(ids({ kinds: [...all.kinds], feeEarnerId: "fe-1", matterId: null })).toEqual(["a", "d"]);
    expect(ids({ kinds: [...all.kinds], feeEarnerId: null, matterId: "m-2" })).toEqual(["b"]);
  });

  it("partitionOverdueBand splits rows dated before the window", () => {
    const events = [
      event({ id: "old", date: "2026-09-30", overdue: true }),
      event({ id: "mon", date: "2026-10-19", overdue: true }),
      event({ id: "fri", date: "2026-10-23" }),
    ];
    const { band, inWindow } = partitionOverdueBand(events, "2026-10-19");
    expect(band.map((e) => e.id)).toEqual(["old"]);
    expect(inWindow.map((e) => e.id)).toEqual(["mon", "fri"]);
  });

  it("groupByDate keeps server order", () => {
    const groups = groupByDate([
      event({ id: "1", date: "2026-10-19" }),
      event({ id: "2", date: "2026-10-20" }),
      event({ id: "3", date: "2026-10-19" }),
    ]);
    expect([...groups.keys()]).toEqual(["2026-10-19", "2026-10-20"]);
    expect(groups.get("2026-10-19")!.map((e) => e.id)).toEqual(["1", "3"]);
  });

  it("formatDiaryTime and humaniseSubtype", () => {
    expect(formatDiaryTime("09:30:00")).toBe("09:30");
    expect(formatDiaryTime("14:05")).toBe("14:05");
    expect(formatDiaryTime(undefined)).toBeNull();
    expect(humaniseSubtype("PRE_TRIAL")).toBe("Pre-trial");
    expect(humaniseSubtype("CASE_MANAGEMENT")).toBe("Case management");
    expect(humaniseSubtype("HEARING")).toBe("Hearing");
    expect(humaniseSubtype(null)).toBeNull();
  });
});

describe("diary window helpers (613C)", () => {
  it("firstOfMonth, lastOfMonth and leap years", () => {
    expect(firstOfMonth("2026-10-21")).toBe("2026-10-01");
    expect(lastOfMonth("2026-10-21")).toBe("2026-10-31");
    expect(lastOfMonth("2026-02-14")).toBe("2026-02-28");
    expect(lastOfMonth("2028-02-01")).toBe("2028-02-29");
  });

  it("addMonths lands on the 1st and never overflows a short month", () => {
    expect(addMonths("2026-01-31", 1)).toBe("2026-02-01");
    expect(addMonths("2026-10-01", -1)).toBe("2026-09-01");
    expect(addMonths("2026-12-15", 1)).toBe("2027-01-01");
    expect(addMonths("2026-01-01", -1)).toBe("2025-12-01");
  });

  it("diaryWindow snaps per view", () => {
    expect(diaryWindow("day", "2026-10-21")).toEqual({ from: "2026-10-21", to: "2026-10-21" });
    expect(diaryWindow("week", "2026-11-01")).toEqual({ from: "2026-10-26", to: "2026-11-01" });
    expect(diaryWindow("month", "2026-10-21")).toEqual({ from: "2026-10-01", to: "2026-10-31" });
    expect(diaryWindow("list", "2026-02-14")).toEqual({ from: "2026-02-01", to: "2026-02-28" });
  });

  it("every window stays within the client-side fee-earner limit", () => {
    for (const view of ["day", "week", "month", "list"] as const) {
      const w = diaryWindow(view, "2026-12-31");
      expect(shouldUseServerFeeEarnerFilter(w.from, w.to)).toBe(false);
    }
  });

  it("stepDiaryWindow moves one day, week or month", () => {
    expect(stepDiaryWindow("day", "2026-10-31", 1)).toEqual({
      from: "2026-11-01",
      to: "2026-11-01",
    });
    expect(stepDiaryWindow("week", "2026-10-19", 1)).toEqual({
      from: "2026-10-26",
      to: "2026-11-01",
    });
    expect(stepDiaryWindow("week", "2026-10-19", -1).from).toBe("2026-10-12");
    expect(stepDiaryWindow("month", "2026-10-01", -1)).toEqual({
      from: "2026-09-01",
      to: "2026-09-30",
    });
    expect(stepDiaryWindow("list", "2026-12-01", 1).from).toBe("2027-01-01");
  });

  it("windowContains and sameWindow", () => {
    const oct = { from: "2026-10-01", to: "2026-10-31" };
    expect(windowContains(oct, "2026-10-01")).toBe(true);
    expect(windowContains(oct, "2026-10-31")).toBe(true);
    expect(windowContains(oct, "2026-11-01")).toBe(false);
    expect(sameWindow(oct, diaryWindow("list", "2026-10-15"))).toBe(true);
    expect(sameWindow(oct, diaryWindow("week", "2026-10-15"))).toBe(false);
  });

  it("monthGridWeeks lays October 2026 out as five Mon–Sun rows", () => {
    const weeks = monthGridWeeks("2026-10-01");
    expect(weeks).toHaveLength(5);
    expect(weeks[0]).toEqual([
      null,
      null,
      null,
      "2026-10-01",
      "2026-10-02",
      "2026-10-03",
      "2026-10-04",
    ]);
    expect(weeks[4]).toEqual([
      "2026-10-26",
      "2026-10-27",
      "2026-10-28",
      "2026-10-29",
      "2026-10-30",
      "2026-10-31",
      null,
    ]);
    expect(weeks.flat().filter(Boolean)).toHaveLength(31);
    // February 2027 starts on a Monday and has 28 days: exactly four full rows.
    expect(monthGridWeeks("2027-02-10")).toHaveLength(4);
  });

  it("formatDiaryRangeLabel per view", () => {
    expect(formatDiaryRangeLabel("day", { from: "2026-10-21", to: "2026-10-21" })).toBe(
      "Wednesday 21 October 2026"
    );
    expect(formatDiaryRangeLabel("week", { from: "2026-10-19", to: "2026-10-25" })).toBe(
      "19 October to 25 October 2026"
    );
    expect(formatDiaryRangeLabel("month", { from: "2026-10-01", to: "2026-10-31" })).toBe(
      "October 2026"
    );
    expect(formatDiaryRangeLabel("list", { from: "2026-10-01", to: "2026-10-31" })).toBe(
      "October 2026"
    );
  });
});
