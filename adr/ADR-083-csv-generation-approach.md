# ADR-083: CSV Generation Approach

**Status**: Accepted

**Context**:

Phase 19 requires CSV export for all report types. The CSV is generated from structured report data (`ReportResult` with `List<Map<String, Object>>` rows and column definitions). The key concern is memory efficiency — report datasets can be large (e.g., a year of time entries for a 50-person org could produce thousands of rows). The CSV export should not accumulate the entire output in memory before writing to the response.

The codebase has no existing CSV generation — this is a new capability. The PDF pipeline (`PdfRenderingService.htmlToPdf()`) uses `ByteArrayOutputStream` which is acceptable for PDFs (binary format, moderate size) but CSV is text and can grow proportionally to dataset size.

**Options Considered**:

1. **In-memory StringBuilder** -- Build the entire CSV as a `String` using `StringBuilder`, then write to the response.
   - Pros: Simple implementation. Easy to unit test (just check the string). Familiar pattern.
   - Cons: For large reports (10,000+ rows), the entire CSV string sits in memory before any bytes are sent to the client. A 10K-row report with 8 columns could produce a 2-5MB string — not catastrophic, but it scales linearly. Multiple concurrent exports multiply the memory pressure.

2. **Streaming via BufferedWriter to ServletOutputStream (chosen)** -- Write CSV rows directly to the `HttpServletResponse` output stream wrapped in a `BufferedWriter`. Each row is written and flushed incrementally.
   - Pros: Constant memory overhead regardless of dataset size. Rows stream to the client as they're written. Simple code — `BufferedWriter.write()` + `newLine()` per row. No external dependencies.
   - Cons: Cannot set `Content-Length` header (unknown until complete). Slightly harder to unit test (need to capture output stream). Error mid-stream produces a partial CSV.

3. **Library-based (Apache Commons CSV)** -- Use Apache Commons CSV (`CSVPrinter`) for formatting and escaping.
   - Pros: Handles all RFC 4180 edge cases (escaping, quoting, null handling). Well-tested library. Can wrap any `Writer` for streaming.
   - Cons: Adds a dependency for a simple task. The DocTeams codebase follows a minimal-dependency philosophy — using a library for CSV formatting (which is ~20 lines of code) is over-engineering. The library's feature set (custom delimiters, quote modes, record separators) is unnecessary here.

**Decision**: Option 2 -- Streaming via `BufferedWriter` to `ServletOutputStream`.

**Rationale**:

The streaming approach is the right fit for a reporting system where dataset sizes are unpredictable. While the initial three reports may produce modest datasets (tens to hundreds of rows), the framework is designed for extensibility — future reports could aggregate much larger datasets. Building for streaming from the start avoids a future refactor when a large-dataset report is added.

The implementation is straightforward: wrap `response.getOutputStream()` in an `OutputStreamWriter` (UTF-8) and `BufferedWriter`, then write header + rows + newlines. CSV escaping (RFC 4180) is a simple rule set: if a value contains commas, double quotes, or newlines, wrap it in double quotes and escape internal double quotes as `""`. This is ~15 lines of Java — no library needed.

The inability to set `Content-Length` is acceptable. Browsers handle chunked transfer encoding for downloads correctly, and the `Content-Disposition: attachment` header ensures the browser treats the response as a file download regardless.

The requirements explicitly state: "Use a simple CSV writer — no need for a library like Apache Commons CSV; Java's built-in capabilities are sufficient." This aligns with the codebase's preference for minimal dependencies.

**Backend impact**: `ReportRenderingService.writeCsv()` accepts an `OutputStream` parameter and writes CSV data via `BufferedWriter`/`OutputStreamWriter` (UTF-8). The `ReportingController` export endpoint passes `response.getOutputStream()` directly. A private `escapeCsv(String)` utility handles RFC 4180 escaping. `ColumnDefinition` records (parsed from `column_definitions` JSON) drive header row and value formatting.

**Consequences**:

- CSV export streams to the client row-by-row via `BufferedWriter` — constant memory overhead.
- No `Content-Length` header on CSV responses (chunked transfer).
- Custom `escapeCsv()` utility method handles RFC 4180 escaping (commas, quotes, newlines).
- Unit tests capture CSV output via `ByteArrayOutputStream` passed to the service method.
- If a report query fails mid-export (after headers are sent), the client receives a partial CSV. This is an inherent limitation of streaming responses and is acceptable — the HTTP status code is already committed as 200.
- No new Maven dependencies added.
