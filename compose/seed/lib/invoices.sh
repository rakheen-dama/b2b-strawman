#!/bin/sh
# compose/seed/lib/invoices.sh — Create invoices with lines
# Requires: lib/common.sh, customer IDs, project IDs

seed_invoices() {
  echo ""
  echo "==> Seeding invoices"
  jwt=$(get_jwt user_e2e_alice owner)

  # Check existing invoices (bare array response)
  existing=$(api_get "/api/invoices?size=200" "$jwt")

  # ── Invoice 1: Acme Corp (manual lines) ─────────────────────────
  # Matched on rich-seed's own notes marker, not the client name: the boot seed (seed_boot_fee_notes)
  # and the e2e specs also create Acme Corp fee notes, and rich-seed must not adopt one of those.
  acme_invoice=$(echo "$existing" | jq -r '.[] | select(.clientName == "Acme Corp" and .notes == "Legacy migration completed work") | .id' 2>/dev/null | head -1)
  if [ -n "$acme_invoice" ] && [ "$acme_invoice" != "null" ]; then
    echo "    [skip] Acme Corp invoice exists (${acme_invoice})"
    ACME_INVOICE_ID="$acme_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"clientId\": \"${ACME_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"Legacy migration completed work\",
      \"paymentTerms\": \"Net 30\"
    }" "$jwt")
    check_status "Create Acme Corp invoice" || return 1
    ACME_INVOICE_ID=$(echo "$body" | jq -r '.id')

    # Add manual lines
    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Database schema migration — 6 hrs",
      "quantity": 6,
      "unitPrice": 150.00,
      "sortOrder": 0
    }' "$jwt" > /dev/null
    check_status "  Line: schema migration"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Data validation scripts — 2 hrs",
      "quantity": 2,
      "unitPrice": 120.00,
      "sortOrder": 1
    }' "$jwt" > /dev/null
    check_status "  Line: data validation"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Project management and coordination",
      "quantity": 1,
      "unitPrice": 250.00,
      "sortOrder": 2
    }' "$jwt" > /dev/null
    check_status "  Line: project management"
  fi

  # ── Invoice 2: Bright Solutions (manual lines, NOT time entries) ─
  bright_invoice=$(echo "$existing" | jq -r '.[] | select(.clientName == "Bright Solutions") | .id' 2>/dev/null | head -1)
  if [ -n "$bright_invoice" ] && [ "$bright_invoice" != "null" ]; then
    echo "    [skip] Bright Solutions invoice exists (${bright_invoice})"
    BRIGHT_INVOICE_ID="$bright_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"clientId\": \"${BRIGHT_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"Mobile App and SEO Audit work\",
      \"paymentTerms\": \"Net 15\"
    }" "$jwt")
    check_status "Create Bright Solutions invoice" || return 1
    BRIGHT_INVOICE_ID=$(echo "$body" | jq -r '.id')

    # Add manual lines
    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "React Native project setup — 3 hrs",
      "quantity": 3,
      "unitPrice": 120.00,
      "sortOrder": 0
    }' "$jwt" > /dev/null
    check_status "  Line: React Native setup"

    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "OAuth integration — 4 hrs",
      "quantity": 4,
      "unitPrice": 120.00,
      "sortOrder": 1
    }' "$jwt" > /dev/null
    check_status "  Line: OAuth integration"

    api_post "/api/invoices/${BRIGHT_INVOICE_ID}/lines" '{
      "description": "SEO audit and report — 6 hrs",
      "quantity": 6,
      "unitPrice": 150.00,
      "sortOrder": 2
    }' "$jwt" > /dev/null
    check_status "  Line: SEO audit"
  fi

  export ACME_INVOICE_ID BRIGHT_INVOICE_ID

  echo ""
  echo "    Invoices seeded:"
  echo "      Acme Corp:        ${ACME_INVOICE_ID}"
  echo "      Bright Solutions: ${BRIGHT_INVOICE_ID}"
}

# ── Boot-seed fee notes (Phase 88 628F, U22) ────────────────────────
# seed_boot_fee_notes — two fee notes for Acme Corp on Website Redesign, both via the API:
#   A "E2E seed fee note — draft": DRAFT, one line.
#   B "E2E seed fee note — sent":  one line, approved (gets an INV-#### number) and sent.
# Requires: ACME_ID, WEBSITE_REDESIGN_ID. Idempotent by notes marker; B is advanced from whatever
# state a previous run left it in. Transitions use check_2xx (409 = refused transition, not "exists").
# Send uses overrideWarnings:false — a 422 means the seed client data is incomplete; fix the data.
SEED_FEE_NOTE_DRAFT="E2E seed fee note — draft"
SEED_FEE_NOTE_SENT="E2E seed fee note — sent"

seed_boot_fee_notes() {
  echo ""
  echo "==> Step 9: Fee notes (1 draft, 1 sent)"
  _fn_jwt=$(get_jwt user_e2e_alice owner)

  _fn_list=$(api_get "/api/invoices?clientId=${ACME_ID}" "$_fn_jwt")
  check_2xx "List Acme fee notes" || return 1

  # ── A: draft ──
  FEE_NOTE_DRAFT_ID=$(echo "$_fn_list" | jq -r --arg n "$SEED_FEE_NOTE_DRAFT" \
    '.[] | select(.notes == $n) | .id' | head -1)
  if [ -n "$FEE_NOTE_DRAFT_ID" ] && [ "$FEE_NOTE_DRAFT_ID" != "null" ]; then
    echo "    [skip] Draft fee note exists (${FEE_NOTE_DRAFT_ID})"
  else
    _fn_body=$(api_post "/api/invoices" "{
      \"clientId\": \"${ACME_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"${SEED_FEE_NOTE_DRAFT}\",
      \"paymentTerms\": \"Net 30\"
    }" "$_fn_jwt")
    check_2xx "Create draft fee note" || return 1
    FEE_NOTE_DRAFT_ID=$(echo "$_fn_body" | jq -r '.id')
    api_post "/api/invoices/${FEE_NOTE_DRAFT_ID}/lines" "{
      \"matterId\": \"${WEBSITE_REDESIGN_ID}\",
      \"description\": \"Consultation — website redesign mandate\",
      \"quantity\": 2,
      \"unitPrice\": 1500.00,
      \"sortOrder\": 0
    }" "$_fn_jwt" > /dev/null
    check_2xx "  Line: consultation" || return 1
  fi

  # ── B: approved + sent ──
  FEE_NOTE_SENT_ID=$(echo "$_fn_list" | jq -r --arg n "$SEED_FEE_NOTE_SENT" \
    '.[] | select(.notes == $n) | .id' | head -1)
  if [ -z "$FEE_NOTE_SENT_ID" ] || [ "$FEE_NOTE_SENT_ID" = "null" ]; then
    _fn_body=$(api_post "/api/invoices" "{
      \"clientId\": \"${ACME_ID}\",
      \"currency\": \"ZAR\",
      \"notes\": \"${SEED_FEE_NOTE_SENT}\",
      \"paymentTerms\": \"Net 30\"
    }" "$_fn_jwt")
    check_2xx "Create fee note to send" || return 1
    FEE_NOTE_SENT_ID=$(echo "$_fn_body" | jq -r '.id')
    api_post "/api/invoices/${FEE_NOTE_SENT_ID}/lines" "{
      \"matterId\": \"${WEBSITE_REDESIGN_ID}\",
      \"description\": \"Drafting — services agreement\",
      \"quantity\": 3,
      \"unitPrice\": 2500.00,
      \"sortOrder\": 0
    }" "$_fn_jwt" > /dev/null
    check_2xx "  Line: drafting" || return 1
  fi

  _fn_json=$(api_get "/api/invoices/${FEE_NOTE_SENT_ID}" "$_fn_jwt")
  check_2xx "Fetch fee note to send" || return 1
  _fn_status=$(echo "$_fn_json" | jq -r '.status')
  if [ "$_fn_status" = "DRAFT" ]; then
    api_post "/api/invoices/${FEE_NOTE_SENT_ID}/approve" '{}' "$_fn_jwt" > /dev/null
    check_2xx "Approve fee note" || return 1
    _fn_status="APPROVED"
  fi
  if [ "$_fn_status" = "APPROVED" ]; then
    api_post "/api/invoices/${FEE_NOTE_SENT_ID}/send" '{"overrideWarnings": false}' "$_fn_jwt" > /dev/null
    check_2xx "Send fee note" || return 1
  else
    echo "    [skip] Fee note already ${_fn_status} (${FEE_NOTE_SENT_ID})"
  fi
  FEE_NOTE_SENT_NUMBER=$(api_get "/api/invoices/${FEE_NOTE_SENT_ID}" "$_fn_jwt" | jq -r '.invoiceNumber')
  echo "    Fee notes: draft ${FEE_NOTE_DRAFT_ID}, sent ${FEE_NOTE_SENT_NUMBER} (${FEE_NOTE_SENT_ID})"
}
