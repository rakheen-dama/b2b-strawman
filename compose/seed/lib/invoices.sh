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
  acme_invoice=$(echo "$existing" | jq -r '.[] | select(.customerName == "Acme Corp") | .id' 2>/dev/null | head -1)
  if [ -n "$acme_invoice" ] && [ "$acme_invoice" != "null" ]; then
    echo "    [skip] Acme Corp invoice exists (${acme_invoice})"
    ACME_INVOICE_ID="$acme_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"customerId\": \"${ACME_ID}\",
      \"currency\": \"USD\",
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
  bright_invoice=$(echo "$existing" | jq -r '.[] | select(.customerName == "Bright Solutions") | .id' 2>/dev/null | head -1)
  if [ -n "$bright_invoice" ] && [ "$bright_invoice" != "null" ]; then
    echo "    [skip] Bright Solutions invoice exists (${bright_invoice})"
    BRIGHT_INVOICE_ID="$bright_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"customerId\": \"${BRIGHT_ID}\",
      \"currency\": \"USD\",
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
