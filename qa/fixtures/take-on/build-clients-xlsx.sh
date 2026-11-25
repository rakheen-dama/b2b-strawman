#!/usr/bin/env bash
# Rebuilds qa/fixtures/take-on/clients.xlsx (task 660.1) — the take-on fixture's two-sheet workbook —
# from xlsx-src/clients-sheet.csv, byte for byte.
#
#   bash qa/fixtures/take-on/build-clients-xlsx.sh          # writes clients.xlsx
#   bash qa/fixtures/take-on/build-clients-xlsx.sh --check  # exits 1 if clients.xlsx is stale
#
# The workbook is the hand-made minimal OOXML the 653A OBS proved the take-on reader accepts
# (qa_cycle/evidence/phase93/653A/obs/fixtures/debtors-two-sheets.xlsx): sheet 1 "Old notes" is
# HIDDEN and holds one cell; sheet 2 "Clients" holds the header in row 1 and the 23 client rows.
# Every cell is an inline string (dates and ID numbers stay text); a blank CSV cell is no cell.
# No spreadsheet library exists in the workspace, so this uses the Python standard library only.
# Reproducible: fixed entry order, fixed timestamps (1 Jan 2026 00:00), entries stored (no deflate).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# No argument rebuilds, `--check` verifies; anything else is refused before the workbook is touched
case "$#:${1:-}" in
  0:) mode=write ;;
  1:--check) mode=--check ;;
  *)
    echo "usage: bash qa/fixtures/take-on/build-clients-xlsx.sh [--check]" >&2
    exit 2
    ;;
esac

python3 -I - "$here/xlsx-src/clients-sheet.csv" "$here/clients.xlsx" "$mode" <<'PY'
import csv, io, sys, zipfile
from xml.sax.saxutils import escape

src, out, mode = sys.argv[1], sys.argv[2], sys.argv[3]
HEAD = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
SHEET_CT = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"


def col(i):
    name = ""
    i += 1
    while i:
        i, r = divmod(i - 1, 26)
        name = chr(65 + r) + name
    return name


def sheet(rows):
    body = []
    for r, values in enumerate(rows, start=1):
        cells = "".join(
            f'<c r="{col(c)}{r}" t="inlineStr"><is><t>{escape(v)}</t></is></c>'
            for c, v in enumerate(values)
            if v != ""
        )
        body.append(f'<row r="{r}">{cells}</row>')
    return f'{HEAD}<worksheet xmlns="{NS}"><sheetData>{"".join(body)}</sheetData></worksheet>'


with open(src, newline="", encoding="utf-8") as f:
    clients = list(csv.reader(f))

parts = [
    (
        "[Content_Types].xml",
        f'{HEAD}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        f'<Override PartName="/xl/worksheets/sheet1.xml" ContentType="{SHEET_CT}"/>'
        f'<Override PartName="/xl/worksheets/sheet2.xml" ContentType="{SHEET_CT}"/>'
        '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
        "</Types>",
    ),
    (
        "_rels/.rels",
        f'{HEAD}<Relationships xmlns="{PKG_REL}"><Relationship Id="rId1" '
        f'Type="{REL}/officeDocument" Target="xl/workbook.xml"/></Relationships>',
    ),
    (
        "xl/workbook.xml",
        f'{HEAD}<workbook xmlns="{NS}" xmlns:r="{REL}"><sheets>'
        '<sheet name="Old notes" sheetId="1" state="hidden" r:id="rId1"/>'
        '<sheet name="Clients" sheetId="2" r:id="rId2"/></sheets></workbook>',
    ),
    (
        "xl/_rels/workbook.xml.rels",
        f'{HEAD}<Relationships xmlns="{PKG_REL}">'
        f'<Relationship Id="rId1" Type="{REL}/worksheet" Target="worksheets/sheet1.xml"/>'
        f'<Relationship Id="rId2" Type="{REL}/worksheet" Target="worksheets/sheet2.xml"/>'
        f'<Relationship Id="rId3" Type="{REL}/styles" Target="styles.xml"/></Relationships>',
    ),
    (
        "xl/styles.xml",
        f'{HEAD}<styleSheet xmlns="{NS}"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>'
        '<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>'
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
        '<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs></styleSheet>',
    ),
    ("xl/worksheets/sheet1.xml", sheet([["internal - do not import"]])),
    ("xl/worksheets/sheet2.xml", sheet(clients)),
]

buf = io.BytesIO()
with zipfile.ZipFile(buf, "w") as z:
    for name, text in parts:
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED  # stored: identical bytes whatever the zlib build
        info.external_attr = 0o644 << 16
        info.create_system = 3
        z.writestr(info, text.encode("utf-8"))
data = buf.getvalue()

if mode == "--check":
    with open(out, "rb") as f:
        current = f.read()
    if current != data:
        sys.exit(f"{out} is stale: run bash qa/fixtures/take-on/build-clients-xlsx.sh")
    print(f"{out} is up to date ({len(data)} bytes)")
else:
    with open(out, "wb") as f:
        f.write(data)
    print(f"wrote {out} ({len(data)} bytes, {len(clients) - 1} client rows)")
PY
