#!/bin/sh
# compose/seed/lib/customers.sh — Create customers across lifecycle stages
# Requires: lib/common.sh sourced first

seed_customers() {
  echo ""
  echo "==> Seeding customers"
  jwt=$(get_jwt user_e2e_alice owner)

  # _ensure_customer <name> <email> <phone> <type> <notes> <target_status>
  _ensure_customer() {
    _ec_name="$1"
    _ec_email="$2"
    _ec_phone="$3"
    _ec_type="$4"
    _ec_notes="$5"
    _ec_target="$6"

    # Check if customer already exists (bare array response)
    _ec_id=$(find_existing "/api/customers" ".[] | select(.name == \"${_ec_name}\")" "$jwt")

    if [ -n "$_ec_id" ] && [ "$_ec_id" != "null" ]; then
      echo "    [skip] ${_ec_name} already exists (${_ec_id})" >&2

      # Check current status and fix if wrong
      _ec_current=$(api_get "/api/customers/${_ec_id}" "$jwt" | jq -r '.lifecycleStatus')

      if [ "$_ec_current" = "$_ec_target" ]; then
        echo "$_ec_id"
        return 0
      fi

      echo "    [fix] ${_ec_name} is ${_ec_current}, target is ${_ec_target}" >&2

      # Drive through transitions to reach target
      case "$_ec_current" in
        PROSPECT)
          if [ "$_ec_target" = "PROSPECT" ]; then echo "$_ec_id"; return 0; fi
          api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
          check_status "${_ec_name} -> ONBOARDING"
          if [ "$_ec_target" = "ONBOARDING" ]; then echo "$_ec_id"; return 0; fi
          complete_checklists "$_ec_id" "$jwt"
          _ec_check=$(api_get "/api/customers/${_ec_id}" "$jwt" | jq -r '.lifecycleStatus')
          if [ "$_ec_check" != "ACTIVE" ]; then
            api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
            check_status "${_ec_name} -> ACTIVE"
          fi
          if [ "$_ec_target" = "ACTIVE" ]; then echo "$_ec_id"; return 0; fi
          if [ "$_ec_target" = "DORMANT" ]; then
            api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"DORMANT","notes":"seed"}' "$jwt" > /dev/null
            check_status "${_ec_name} -> DORMANT"
          fi
          ;;
        ONBOARDING)
          if [ "$_ec_target" = "ONBOARDING" ]; then echo "$_ec_id"; return 0; fi
          complete_checklists "$_ec_id" "$jwt"
          _ec_check=$(api_get "/api/customers/${_ec_id}" "$jwt" | jq -r '.lifecycleStatus')
          if [ "$_ec_check" != "ACTIVE" ]; then
            api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
            check_status "${_ec_name} -> ACTIVE"
          fi
          if [ "$_ec_target" = "ACTIVE" ]; then echo "$_ec_id"; return 0; fi
          if [ "$_ec_target" = "DORMANT" ]; then
            api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"DORMANT","notes":"seed"}' "$jwt" > /dev/null
            check_status "${_ec_name} -> DORMANT"
          fi
          ;;
        ACTIVE)
          if [ "$_ec_target" = "ACTIVE" ]; then echo "$_ec_id"; return 0; fi
          if [ "$_ec_target" = "DORMANT" ]; then
            api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"DORMANT","notes":"seed"}' "$jwt" > /dev/null
            check_status "${_ec_name} -> DORMANT"
          fi
          ;;
        DORMANT)
          if [ "$_ec_target" = "DORMANT" ]; then echo "$_ec_id"; return 0; fi
          ;;
      esac

      echo "$_ec_id"
      return 0
    fi

    # Create new customer
    _ec_body=$(api_post "/api/customers" "{
      \"name\": \"${_ec_name}\",
      \"email\": \"${_ec_email}\",
      \"phone\": \"${_ec_phone}\",
      \"type\": \"${_ec_type}\",
      \"notes\": \"${_ec_notes}\"
    }" "$jwt")
    check_status "Create ${_ec_name}" || return 1
    _ec_id=$(echo "$_ec_body" | jq -r '.id')

    # Transition from PROSPECT to target status
    if [ "$_ec_target" = "PROSPECT" ]; then
      echo "$_ec_id"
      return 0
    fi

    api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
    check_status "${_ec_name} -> ONBOARDING"

    if [ "$_ec_target" = "ONBOARDING" ]; then
      echo "$_ec_id"
      return 0
    fi

    # Complete checklists to allow ACTIVE transition (auto-transitions when all done)
    complete_checklists "$_ec_id" "$jwt"
    _ec_check=$(api_get "/api/customers/${_ec_id}" "$jwt" | jq -r '.lifecycleStatus')
    if [ "$_ec_check" != "ACTIVE" ]; then
      api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
      check_status "${_ec_name} -> ACTIVE"
    else
      echo "    [ok] ${_ec_name} auto-transitioned to ACTIVE" >&2
    fi

    if [ "$_ec_target" = "ACTIVE" ]; then
      echo "$_ec_id"
      return 0
    fi

    if [ "$_ec_target" = "DORMANT" ]; then
      api_post "/api/customers/${_ec_id}/transition" '{"targetStatus":"DORMANT","notes":"seed"}' "$jwt" > /dev/null
      check_status "${_ec_name} -> DORMANT"
    fi

    echo "$_ec_id"
  }

  # ── Acme Corp (ACTIVE, COMPANY) ──────────────────────────────────
  ACME_ID=$(_ensure_customer "Acme Corp" "contact@acme.example.com" "+1-555-0100" "COMPANY" "Primary test customer" "ACTIVE")

  # ── Bright Solutions (ACTIVE, COMPANY) ───────────────────────────
  BRIGHT_ID=$(_ensure_customer "Bright Solutions" "hello@brightsolutions.example.com" "+1-555-0200" "COMPANY" "Secondary test customer" "ACTIVE")

  # ── Carlos Mendez (ONBOARDING, INDIVIDUAL) ───────────────────────
  CARLOS_ID=$(_ensure_customer "Carlos Mendez" "carlos@mendez.example.com" "+1-555-0300" "INDIVIDUAL" "Individual client in onboarding" "ONBOARDING")

  # ── Dormant Industries (DORMANT, COMPANY) ────────────────────────
  DORMANT_ID=$(_ensure_customer "Dormant Industries" "info@dormant.example.com" "+1-555-0400" "COMPANY" "Formerly active, now dormant" "DORMANT")

  # Export IDs for downstream modules
  export ACME_ID BRIGHT_ID CARLOS_ID DORMANT_ID

  echo ""
  echo "    Customers seeded:"
  echo "      Acme Corp:          ${ACME_ID}"
  echo "      Bright Solutions:   ${BRIGHT_ID}"
  echo "      Carlos Mendez:      ${CARLOS_ID}"
  echo "      Dormant Industries: ${DORMANT_ID}"
}
