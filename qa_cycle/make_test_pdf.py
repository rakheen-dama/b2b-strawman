#!/usr/bin/env python3
"""
Generate well-formed single-page PDFs for QA test fixtures.

Produces valid PDF 1.4 files with:
- Proper header (%PDF-1.4 + binary marker)
- Catalog, Pages, Page, Content stream, Font objects
- Correct xref byte offsets
- Trailer with /Size /Root
- startxref + %%EOF

Verified openable in macOS Preview, Adobe Reader, and pdftotext.
"""

import sys
from pathlib import Path
from typing import List


def escape_pdf_text(s: str) -> str:
    """Escape characters that are special inside a PDF string literal."""
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def build_content_stream(title: str, body_lines: List[str]) -> bytes:
    """
    Build the page content stream (text commands only).
    Uses Helvetica (F1) with 16pt title and 11pt body.
    """
    lines = ["BT", "/F1 16 Tf", "1 0 0 1 72 740 Tm", f"({escape_pdf_text(title)}) Tj"]
    lines.append("/F1 11 Tf")
    y = 710
    for body_line in body_lines:
        lines.append(f"1 0 0 1 72 {y} Tm")
        lines.append(f"({escape_pdf_text(body_line)}) Tj")
        y -= 16
    lines.append("ET")
    stream = "\n".join(lines) + "\n"
    # Font uses WinAnsiEncoding (≈ CP1252), which covers smart quotes,
    # em/en dashes, ellipses, and the ≥/≤ range used in legal copy.
    return stream.encode("cp1252", errors="replace")


def make_pdf(title: str, body_lines: List[str], out_path: Path) -> int:
    """
    Build a PDF byte-by-byte with correct xref offsets.
    Returns the number of bytes written.
    """
    header = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"

    content = build_content_stream(title, body_lines)

    objects: List[bytes] = []

    # Object 1: Catalog
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    # Object 2: Pages
    objects.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    # Object 3: Page (letter size 612x792)
    objects.append(
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>"
    )
    # Object 4: Content stream
    content_obj = b"<< /Length " + str(len(content)).encode("ascii") + b" >>\nstream\n" + content + b"endstream"
    objects.append(content_obj)
    # Object 5: Font
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

    # Assemble the body while tracking byte offsets
    buf = bytearray(header)
    offsets: List[int] = []
    for i, obj in enumerate(objects, start=1):
        offsets.append(len(buf))
        buf.extend(f"{i} 0 obj\n".encode("ascii"))
        buf.extend(obj)
        buf.extend(b"\nendobj\n")

    # xref table
    xref_offset = len(buf)
    num_entries = len(objects) + 1  # +1 for the free object 0
    buf.extend(f"xref\n0 {num_entries}\n".encode("ascii"))
    buf.extend(b"0000000000 65535 f \n")
    for off in offsets:
        buf.extend(f"{off:010d} 00000 n \n".encode("ascii"))

    # Trailer
    buf.extend(f"trailer\n<< /Size {num_entries} /Root 1 0 R >>\n".encode("ascii"))
    buf.extend(f"startxref\n{xref_offset}\n".encode("ascii"))
    buf.extend(b"%%EOF\n")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(bytes(buf))
    return len(buf)


# --- Document catalog ----------------------------------------------------

DOCS = {
    # Generic FICA / KYC / engagement docs (used by consulting + legal flows)
    "test-doc.pdf": (
        "Test Document",
        ["This is a generic test document used for upload verification.",
         "Generated as a well-formed single-page PDF for QA fixtures.",
         "If you can read this, the PDF is valid."],
    ),
    "signed-engagement-letter.pdf": (
        "Engagement Letter — Signed",
        ["This agreement is entered into between the Client and the Firm.",
         "Scope of work, fee structure, and termination clauses apply.",
         "Signed: Jane Doe (Client)    Date: 2026-04-17",
         "Signed: John Smith (Firm Partner)    Date: 2026-04-17"],
    ),
    "engagement-letter.pdf": (
        "Engagement Letter",
        ["Draft engagement letter awaiting signature.",
         "Scope: Legal services as instructed.",
         "Fee: Hourly billing at agreed rate card.",
         "Term: From signature until closure of the matter."],
    ),
    "engagement-letter-signed.pdf": (
        "Engagement Letter — Signed",
        ["This letter confirms the firm's engagement to act on the matter.",
         "Countersigned by both parties on 2026-04-17."],
    ),
    # FICA (Financial Intelligence Centre Act — SA KYC) docs
    "id-document.pdf": (
        "Identity Document — Copy",
        ["Surname: DLAMINI",
         "Forename(s): Sipho Thabo",
         "ID Number: 850315 5000 08 5",
         "Date of Birth: 15 March 1985",
         "Citizenship: South African",
         "Issued by: Department of Home Affairs"],
    ),
    "certified-id-copy.pdf": (
        "Certified Copy of Identity Document",
        ["I, the undersigned Commissioner of Oaths, certify that",
         "this is a true copy of the original identity document.",
         "Commissioner: Capt. L. van der Merwe",
         "SAPS Station: Cape Town Central",
         "Date: 2026-04-10"],
    ),
    "proof-of-address.pdf": (
        "Proof of Address — Municipal Bill",
        ["Account Holder: S. Dlamini",
         "Service Address: 42 Long Street, Cape Town, 8001",
         "Period: March 2026",
         "Amount: R 1,247.50",
         "Issued by: City of Cape Town Municipality"],
    ),
    "source-of-funds.pdf": (
        "Source of Funds Declaration",
        ["Client declares funds originate from salary employment",
         "with Standard Bank of South Africa (Pty) Ltd since 2015.",
         "Monthly gross income: R 78,000.",
         "Declaration signed under oath on 2026-04-10."],
    ),
    "source-of-funds-declaration.pdf": (
        "Source of Funds Declaration",
        ["Declaration of the origin of funds deposited in trust.",
         "Source: Proceeds from sale of primary residence.",
         "Sale date: 2026-03-28. Amount: R 1,850,000.",
         "Supporting documents attached: conveyancer's statement."],
    ),
    "beneficial-ownership.pdf": (
        "Beneficial Ownership Declaration",
        ["Entity: Ubuntu Holdings (Pty) Ltd",
         "Ultimate Beneficial Owners:",
         " 1. Sipho Dlamini — 60% (Director)",
         " 2. Nomsa Khumalo — 40% (Director)",
         "No politically exposed persons identified."],
    ),
    "beneficial-ownership-declaration.pdf": (
        "Beneficial Ownership Declaration",
        ["Declaration per FIC Act, Schedule 3A requirements.",
         "Entity Type: Private Company (Pty) Ltd",
         "All UBOs holding ≥ 5% disclosed above.",
         "Declaration made on 2026-04-10."],
    ),
    "power-of-attorney.pdf": (
        "Power of Attorney",
        ["I, Sipho Dlamini, ID 850315 5000 08 5, hereby appoint",
         "Smith Attorneys Inc. as my lawful attorney to act on",
         "my behalf in all matters related to case number",
         "CPT/CIV/2026/0042. Executed under seal on 2026-04-10."],
    ),
    # Trust-account / FIC related (used on the trust-accounting module)
    "trust-deed.pdf": (
        "Trust Deed",
        ["Trust Name: Dlamini Family Trust",
         "Trust Number: IT 1234/2020 (Cape Town)",
         "Founder: Sipho Dlamini",
         "Trustees: Sipho Dlamini, Nomsa Khumalo, Thandi Smith",
         "Beneficiaries: as defined in Schedule A",
         "Registered by the Master of the High Court, WC Division."],
    ),
    "trustee1-id.pdf": (
        "Trustee 1 — Identity Document",
        ["Trustee 1: Sipho Dlamini",
         "ID Number: 850315 5000 08 5",
         "Appointed: 2020-06-15"],
    ),
    "trustee2-id.pdf": (
        "Trustee 2 — Identity Document",
        ["Trustee 2: Nomsa Khumalo",
         "ID Number: 870922 0123 08 4",
         "Appointed: 2020-06-15"],
    ),
    "letters-of-authority.pdf": (
        "Letters of Authority",
        ["Issued by the Master of the High Court, Cape Town.",
         "Reference: IT 1234/2020",
         "The trustees named herein are hereby authorised to act",
         "on behalf of the Dlamini Family Trust.",
         "Issued: 2020-07-01."],
    ),
    "trust-banking-confirmation.pdf": (
        "Trust Banking Confirmation",
        ["Bank: Standard Bank of South Africa",
         "Branch: Cape Town Central (051001)",
         "Account Name: Smith Attorneys Trust Account",
         "Account Number: 012-345-678",
         "Type: Section 86(4) Legal Practice Act Trust Account"],
    ),
    # FICA short-name aliases (used by consulting test-files naming)
    "test-fica-id.pdf": ("FICA — Identity Document",
                        ["Test FICA fixture — ID document.", "Valid SA ID format."]),
    "test-fica-address.pdf": ("FICA — Proof of Address",
                             ["Test FICA fixture — proof of address.", "Municipal bill format."]),
    "test-fica-engagement.pdf": ("FICA — Engagement Letter",
                                ["Test FICA fixture — engagement letter.", "Countersigned."]),
    "test-fica-funds.pdf": ("FICA — Source of Funds",
                           ["Test FICA fixture — source of funds.", "Salary-derived."]),
    "test-fica-beneficial.pdf": ("FICA — Beneficial Ownership",
                                ["Test FICA fixture — beneficial ownership declaration.", "No PEPs."]),
    # Legal-matter content docs
    "particulars-of-claim-draft-v1.pdf": (
        "Particulars of Claim — Draft v1",
        ["IN THE HIGH COURT OF SOUTH AFRICA",
         "(WESTERN CAPE DIVISION, CAPE TOWN)",
         "Case No: CPT/CIV/2026/0042",
         "Sipho Dlamini     (Plaintiff)",
         "v.",
         "Standard Bank of South Africa Ltd     (Defendant)",
         "PARTICULARS OF CLAIM",
         "1. The Plaintiff is an adult male businessman...",
         "2. The Defendant is a registered commercial bank..."],
    ),
}


def main(out_dir: Path, only: List[str] | None = None) -> None:
    count = 0
    for name, (title, body) in DOCS.items():
        if only and name not in only:
            continue
        out = out_dir / name
        size = make_pdf(title, body, out)
        print(f"  wrote {out} ({size} B)")
        count += 1
    print(f"Generated {count} PDFs in {out_dir}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: make_test_pdf.py <out_dir> [filename.pdf ...]", file=sys.stderr)
        sys.exit(1)
    out_dir = Path(sys.argv[1])
    only = sys.argv[2:] if len(sys.argv) > 2 else None
    main(out_dir, only)
